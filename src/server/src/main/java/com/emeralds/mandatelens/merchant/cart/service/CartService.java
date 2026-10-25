package com.emeralds.mandatelens.merchant.cart.service;

import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import com.emeralds.mandatelens.merchant.catalog.model.Product;
import com.emeralds.mandatelens.merchant.catalog.service.ProductService;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartItemRequest;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Stores merchant carts in PostgreSQL and calculates item values from the catalog.
 * The cart types used here are internal domain types, not PayPal wire models.
 * Cart IDs also correlate the lifecycle events written to AgentPulse.
 */
@Service
public class CartService {
    private static final Logger logger = LoggerFactory.getLogger(CartService.class);

    private final ProductService productService;
    private final EventStore eventStore;
    private final JdbcTemplate jdbc;

    public CartService(ProductService productService, EventStore eventStore, JdbcTemplate jdbc) {
        this.productService = productService;
        this.eventStore = eventStore;
        this.jdbc = jdbc;
    }

    /** Creates a cart after resolving every requested variant against the catalog. */
    @Transactional
    public Cart create(List<CartItemRequest> items) throws IOException {
        String cartId = newCartId();
        Cart cart = previewCreate(cartId, cartId, items);
        commitCreate(cart);
        return cart;
    }

    /** Resolves a new cart without persisting it, so policy can be checked first. */
    public Cart previewCreate(List<CartItemRequest> items) throws IOException {
        String cartId = newCartId();
        return previewCreate(cartId, cartId, items);
    }

    /** Resolves a new cart and binds it to the caller's trust journey. */
    public Cart previewCreate(String journeyId, List<CartItemRequest> items) throws IOException {
        return previewCreate(newCartId(), journeyId, items);
    }

    private Cart previewCreate(String cartId, String journeyId, List<CartItemRequest> items) throws IOException {
        if (journeyId == null || journeyId.isBlank()) throw new IllegalArgumentException("journeyId is required");
        return buildCart(cartId, journeyId, items);
    }

    /** Persists a previously resolved cart after merchant-side policy checks pass. */
    @Transactional
    public void commitCreate(Cart cart) {
        jdbc.update("""
                INSERT INTO merchant.carts (cart_id, mandatelens_journey_id, source_channel, currency, subtotal, total)
                VALUES (?, ?, 'STORESYNC', ?, ?, ?)
                """, cart.id(), cart.journeyId(), cart.currency(), cart.subtotal(), cart.subtotal());
        jdbc.update("UPDATE trust.trust_journeys SET cart_id = ?, updated_at = NOW() WHERE journey_id = ?",
                cart.id(), cart.journeyId());
        saveLines(cart);
        recordCartEvent(cart, "CART_CREATED");
        logger.info("Cart persisted: action=created cartId={} journeyId={} itemCount={} total={} currency={}",
                cart.id(), cart.journeyId(), cart.items().size(), cart.subtotal(), cart.currency());
    }

    /** Replaces all lines, re-resolves catalog values, and recalculates the subtotal. */
    @Transactional
    public Cart replace(String cartId, List<CartItemRequest> items) throws IOException {
        Cart updated = previewReplace(cartId, items);
        commitReplace(updated);
        return updated;
    }

    /** Resolves a replacement without changing cart state or recording an event. */
    public Cart previewReplace(String cartId, List<CartItemRequest> items) throws IOException {
        if (!exists(cartId)) {
            throw new NoSuchElementException("Cart not found: " + cartId);
        }
        if (items == null || items.isEmpty()) {
            Cart existing = get(cartId);
            return new Cart(cartId, existing.journeyId(), List.of(), BigDecimal.ZERO,
                    existing.currency(), List.of());
        }
        Cart existing = get(cartId);
        return buildCart(cartId, existing.journeyId(), items);
    }

    /** Persists a previously resolved replacement after merchant-side policy checks pass. */
    @Transactional
    public void commitReplace(Cart updated) {
        if (!exists(updated.id())) throw new NoSuchElementException("Cart not found: " + updated.id());
        jdbc.update("DELETE FROM merchant.cart_items WHERE cart_id = ?", updated.id());
        jdbc.update("""
                UPDATE merchant.carts SET currency = ?, subtotal = ?, total = ?, updated_at = NOW()
                WHERE cart_id = ?
                """, updated.currency(), updated.subtotal(), updated.subtotal(), updated.id());
        saveLines(updated);
        recordCartEvent(updated, "CART_REPLACED");
        logger.info("Cart persisted: action=replaced cartId={} journeyId={} itemCount={} total={} currency={}",
                updated.id(), updated.journeyId(), updated.items().size(), updated.subtotal(), updated.currency());
    }

    /** Loads a cart from PostgreSQL, or throws when its ID is unknown. */
    public Cart get(String cartId) {
        if (!exists(cartId)) throw new NoSuchElementException("Cart not found: " + cartId);
        CartIdentity identity = jdbc.queryForObject("""
                SELECT mandatelens_journey_id, currency FROM merchant.carts WHERE cart_id = ?
                """, (rs, row) -> new CartIdentity(rs.getString("mandatelens_journey_id"),
                rs.getString("currency").trim()), cartId);
        String currency = identity.currency();
        List<CartLine> lines = jdbc.query("""
                SELECT c.currency, i.product_id, i.quantity,
                       i.product_name_snapshot, i.category_snapshot, i.unit_price,
                       p.is_active, p.available_quantity
                FROM merchant.carts c JOIN merchant.cart_items i ON i.cart_id = c.cart_id
                JOIN merchant.products p ON p.product_id = i.product_id
                WHERE c.cart_id = ? ORDER BY i.created_at, i.product_id
                """, (rs, row) -> new CartLine(rs.getString("product_id"), rs.getInt("quantity"),
                rs.getString("product_name_snapshot"), rs.getString("category_snapshot"),
                rs.getBigDecimal("unit_price"), rs.getString("currency").trim(),
                rs.getBoolean("is_active") && rs.getInt("available_quantity") > 0 ? "IN_STOCK" : "OUT_OF_STOCK",
                rs.getInt("available_quantity")), cartId);
        BigDecimal subtotal = lines.stream().map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> issues = lines.stream().filter(line -> !"IN_STOCK".equals(line.availability())
                || line.quantity() > line.availableQuantity()).map(CartLine::variantId).toList();
        return new Cart(cartId, identity.journeyId() == null ? cartId : identity.journeyId(),
                lines, subtotal, currency, issues);
    }

    /** Builds catalog-priced lines and records out-of-stock or insufficient-quantity variants. */
    private Cart buildCart(String cartId, String journeyId, List<CartItemRequest> requestedItems) throws IOException {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("A cart must contain at least one item");
        }

        Map<String, Product> productsById = productService.getProducts().stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
        List<CartLine> lines = new ArrayList<>();
        List<String> availabilityIssues = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        String currency = null;

        for (CartItemRequest requested : requestedItems) {
            if (requested == null || requested.variantId() == null || requested.variantId().isBlank()) {
                throw new IllegalArgumentException("Each cart item must have a variant ID");
            }
            if (requested.quantity() < 1) {
                throw new IllegalArgumentException("Cart item quantity must be at least one");
            }

            Product product = productsById.get(requested.variantId());
            if (product == null) {
                throw new IllegalArgumentException("Unknown catalog variant: " + requested.variantId());
            }
            if (currency != null && !currency.equals(product.getCurrency())) {
                throw new IllegalArgumentException("A cart cannot contain multiple currencies");
            }
            currency = product.getCurrency();

            lines.add(new CartLine(product.getId(), requested.quantity(), product.getName(), product.getCategory(),
                    product.getPrice(), product.getCurrency(), product.getAvailability(),
                    product.getAvailableQuantity()));
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(requested.quantity())));
            if (!"IN_STOCK".equalsIgnoreCase(product.getAvailability())
                    || requested.quantity() > product.getAvailableQuantity()) {
                availabilityIssues.add(product.getId());
            }
        }

        return new Cart(cartId, journeyId, lines, subtotal, currency, availabilityIssues);
    }

    /** Records a minimal lifecycle event against the journey that owns the cart. */
    private void recordCartEvent(Cart cart, String type) {
        String itemSummary = cart.items().stream()
                .map(item -> item.variantId() + "x" + item.quantity())
                .collect(java.util.stream.Collectors.joining(","));
        String details = "items=" + itemSummary + ";availabilityIssues="
                + String.join(",", cart.availabilityIssues());
        eventStore.save(new PurchaseEvent(
                "EVENT-" + UUID.randomUUID().toString(),
                cart.journeyId(),
                Instant.now(),
                type,
                "MERCHANT_API",
                cart.subtotal(),
                cart.currency(),
                details));
    }

    private String newCartId() {
        return "CART-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private boolean exists(String cartId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM merchant.carts WHERE cart_id = ?)", Boolean.class, cartId));
    }

    private record CartIdentity(String journeyId, String currency) { }

    private void saveLines(Cart cart) {
        for (CartLine item : cart.items()) {
            jdbc.update("""
                    INSERT INTO merchant.cart_items
                    (cart_id, product_id, product_name_snapshot, category_snapshot, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, cart.id(), item.variantId(), item.name(), item.category(), item.quantity(), item.unitPrice());
        }
    }
}
