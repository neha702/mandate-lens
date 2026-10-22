package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartItemRequest;
import com.emeralds.mandatelens.merchant.cart.service.CartService;
import com.emeralds.mandatelens.mandate.model.Mandate;
import com.emeralds.mandatelens.mandate.service.MandateService;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartResponse;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCheckoutRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps Store Sync wire requests to the merchant cart and PayPal Orders v2. */
@Service
public class StoreSyncCartService {
    private final CartService cartService;
    private final PayPalOrdersClient ordersClient;
    private final EventStore eventStore;
    private final StoreSyncOrderRepository orderRepository;
    private final StoreSyncGuardService agentGuard;
    private final MandateService mandateService;
    private final String demoJourneyId;

    public StoreSyncCartService(CartService cartService, PayPalOrdersClient ordersClient,
                                EventStore eventStore, StoreSyncOrderRepository orderRepository,
                                StoreSyncGuardService agentGuard, MandateService mandateService,
                                @Value("${agentguard.demo.journey-id:demo-shoes-001}") String demoJourneyId) {
        this.cartService = cartService;
        this.ordersClient = ordersClient;
        this.eventStore = eventStore;
        this.orderRepository = orderRepository;
        this.agentGuard = agentGuard;
        this.mandateService = mandateService;
        this.demoJourneyId = demoJourneyId;
    }

    public StoreSyncCartResponse create(StoreSyncCartRequest request) throws IOException {
        GuardContext guardContext = resolveGuardContext();
        Cart cart = cartService.create(toCartItems(request.items()));
        StoreSyncOrderRepository.CartDetails details = cartDetails(request);
        orderRepository.saveCartDetails(cart.id(), details);
        orderRepository.saveGuardContext(cart.id(), guardContext);
        StoreSyncOrderRepository.OrderState order = null;
        if (cart.availabilityIssues().isEmpty()) {
            PayPalOrdersClient.OrderResult result = ordersClient.createOrder(cart.id(), orderBody(cart, details));
            order = new StoreSyncOrderRepository.OrderState(result.orderId(), 0, result.approvalUrl());
            orderRepository.saveOrder(cart.id(), order);
        }
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE", order, issues);
    }

    public StoreSyncCartResponse replace(String cartId, StoreSyncCartRequest request) throws IOException {
        if (orderRepository.findCompletedCart(cartId).isPresent()) {
            throw new IllegalStateException("A completed cart cannot be changed.");
        }
        StoreSyncOrderRepository.CartDetails previousDetails = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext guardContext = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        Cart currentCart = cartService.get(cartId);
        Cart cart = cartService.previewReplace(cartId, toCartItems(request.items()));
        StoreSyncOrderRepository.CartDetails details = cartDetails(request);
        agentGuard.evaluateCart(cartId, guardContext, currentCart, country(previousDetails), cart, country(details));
        cartService.commitReplace(cart);
        orderRepository.saveCartDetails(cart.id(), details);
        StoreSyncOrderRepository.OrderState order = orderRepository.findOrder(cartId).orElse(null);
        if (cart.availabilityIssues().isEmpty()) {
            if (order == null) {
                PayPalOrdersClient.OrderResult result = ordersClient.createOrder(cart.id(), orderBody(cart, details));
                order = new StoreSyncOrderRepository.OrderState(result.orderId(), 0, result.approvalUrl());
            } else {
                int revision = order.revision() + 1;
                ordersClient.updateOrder(cartId, revision, order.orderId(), amount(cart), paypalItems(cart),
                        shippingDetails(details), previousDetails != null && previousDetails.shippingAddress() != null);
                order = new StoreSyncOrderRepository.OrderState(order.orderId(), revision, order.approvalUrl());
            }
            orderRepository.saveOrder(cartId, order);
        }
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE", order, issues);
    }

    public StoreSyncCartResponse get(String cartId) {
        StoreSyncCartResponse completed = orderRepository.findCompletedCart(cartId).orElse(null);
        if (completed != null) return completed;
        Cart cart = cartService.get(cartId);
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE",
                orderRepository.findOrder(cartId).orElse(null), issues);
    }

    public StoreSyncCartResponse checkout(String cartId, StoreSyncCheckoutRequest request) throws IOException {
        StoreSyncCartResponse completed = orderRepository.findCompletedCart(cartId).orElse(null);
        if (completed != null) return completed;
        Cart cart = cartService.get(cartId);
        if (!cart.availabilityIssues().isEmpty()) {
            return response(cart, "INCOMPLETE", orderRepository.findOrder(cartId).orElse(null), inventoryIssues(cart));
        }
        if (request.paymentMethod() == null || request.paymentMethod().type() == null
                || !"paypal".equalsIgnoreCase(request.paymentMethod().type())) {
            throw new IllegalArgumentException("payment_method.type must be paypal.");
        }
        StoreSyncOrderRepository.OrderState order = orderRepository.findOrder(cartId).orElse(null);
        String requestedOrderId = request.paymentMethod().token();
        if (order == null || requestedOrderId == null || !order.orderId().equals(requestedOrderId)) {
            throw new IllegalArgumentException("payment_method.token must match this cart's PayPal order ID.");
        }

        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext guardContext = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        agentGuard.evaluateCheckout(cartId, guardContext, cart, country(details));

        PayPalOrdersClient.CaptureResult capture;
        try {
            capture = ordersClient.captureOrder(cartId, order.orderId());
        } catch (PayPalOrdersClient.PayPalGatewayException exception) {
            if (exception.httpStatus() == 422) {
                StoreSyncCartResponse.ValidationIssue paymentIssue = new StoreSyncCartResponse.ValidationIssue(
                        "PAYMENT_ERROR", "BUSINESS_RULE", "PayPal could not complete this payment.",
                        "Choose another payment method and try again.", null);
                return response(cart, "INCOMPLETE", order, List.of(paymentIssue));
            }
            throw exception;
        }
        StoreSyncCartResponse response = response(cart, "COMPLETED", order, List.of(),
                new StoreSyncCartResponse.PaymentConfirmation(order.orderId(), null));
        eventStore.save(new PurchaseEvent("EVENT-" + UUID.randomUUID(), cartId, Instant.now(),
                "PAYMENT_CAPTURED", "PAYPAL_ORDERS_V2", cart.subtotal(), cart.currency(),
                "orderId=" + order.orderId() + ";captureId=" + capture.captureId()));
        orderRepository.saveCompletedCart(cartId, response, capture.captureId());
        return response;
    }

    private List<CartItemRequest> toCartItems(List<StoreSyncCartRequest.Item> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items must contain at least one item.");
        return items.stream().map(item -> new CartItemRequest(item.catalogVariantId(), item.quantity())).toList();
    }

    private Map<String, Object> orderBody(Cart cart, StoreSyncOrderRepository.CartDetails details) {
        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("reference_id", "default");
        purchaseUnit.put("amount", amount(cart));
        purchaseUnit.put("items", paypalItems(cart));
        Map<String, Object> shipping = shippingDetails(details);
        if (shipping != null) purchaseUnit.put("shipping", shipping);
        return Map.of("intent", "CAPTURE", "purchase_units", List.of(purchaseUnit));
    }

    private List<Map<String, Object>> paypalItems(Cart cart) {
        return cart.items().stream().map(item -> Map.<String, Object>of(
                "name", item.name(),
                "quantity", Integer.toString(item.quantity()),
                "unit_amount", money(item.unitPrice(), item.currency()))).toList();
    }

    private Map<String, Object> shippingDetails(StoreSyncOrderRepository.CartDetails details) {
        if (details == null || details.shippingAddress() == null) return null;
        StoreSyncCartRequest.Address address = details.shippingAddress();
        Map<String, Object> paypalAddress = new LinkedHashMap<>();
        putIfPresent(paypalAddress, "address_line_1", address.addressLine1());
        putIfPresent(paypalAddress, "address_line_2", address.addressLine2());
        putIfPresent(paypalAddress, "admin_area_2", address.adminArea2());
        putIfPresent(paypalAddress, "admin_area_1", address.adminArea1());
        putIfPresent(paypalAddress, "postal_code", address.postalCode());
        putIfPresent(paypalAddress, "country_code", address.countryCode());

        Map<String, Object> shipping = new LinkedHashMap<>();
        StoreSyncCartRequest.Customer customer = details.customer();
        if (customer != null && customer.name() != null) {
            String fullName = String.join(" ", java.util.stream.Stream.of(
                    customer.name().givenName(), customer.name().surname())
                    .filter(value -> value != null && !value.isBlank()).toList());
            putIfPresent(shipping, "name", fullName.isBlank() ? null : Map.of("full_name", fullName));
        }
        if (customer != null) putIfPresent(shipping, "email_address", customer.emailAddress());
        shipping.put("address", paypalAddress);
        return shipping;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private Map<String, Object> amount(Cart cart) {
        Map<String, Object> subtotal = money(cart.subtotal(), cart.currency());
        return Map.of("currency_code", cart.currency(), "value", decimal(cart.subtotal()),
                "breakdown", Map.of("item_total", subtotal));
    }

    private Map<String, Object> money(BigDecimal amount, String currency) {
        return Map.of("currency_code", currency, "value", decimal(amount));
    }

    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private StoreSyncCartResponse response(Cart cart, String status, StoreSyncOrderRepository.OrderState order,
                                           List<StoreSyncCartResponse.ValidationIssue> issues) {
        return response(cart, status, order, issues, null);
    }

    private StoreSyncCartResponse response(Cart cart, String status, StoreSyncOrderRepository.OrderState order,
                                          List<StoreSyncCartResponse.ValidationIssue> issues,
                                          StoreSyncCartResponse.PaymentConfirmation confirmation) {
        List<StoreSyncCartResponse.Item> items = cart.items().stream().map(item ->
                new StoreSyncCartResponse.Item(item.variantId(), item.quantity(), item.name(),
                        new StoreSyncCartResponse.Money(item.currency(), decimal(item.unitPrice())),
                        new StoreSyncCartResponse.Money(item.currency(),
                                decimal(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))))).toList();
        StoreSyncCartResponse.Money total = new StoreSyncCartResponse.Money(cart.currency(), decimal(cart.subtotal()));
        StoreSyncCartResponse.Totals totals = new StoreSyncCartResponse.Totals(total, total, zero(cart.currency()), zero(cart.currency()));
        StoreSyncCartResponse.PaymentMethod paymentMethod = order == null ? null
                : new StoreSyncCartResponse.PaymentMethod("paypal", order.orderId(), null, order.approvalUrl());
        String validationStatus = issues.isEmpty() ? "VALID" : "INVALID";
        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cart.id()).orElse(null);
        return new StoreSyncCartResponse(cart.id(), status, validationStatus, issues, items, totals,
                details == null ? null : details.customer(),
                details == null ? null : details.shippingAddress(),
                details == null ? null : details.billingAddress(),
                paymentMethod, confirmation);
    }

    private StoreSyncOrderRepository.CartDetails cartDetails(StoreSyncCartRequest request) {
        return new StoreSyncOrderRepository.CartDetails(
                request.customer(), request.shippingAddress(), request.billingAddress());
    }

    private String country(StoreSyncOrderRepository.CartDetails details) {
        return details == null || details.shippingAddress() == null ? null
                : details.shippingAddress().countryCode();
    }

    private GuardContext resolveGuardContext() {
        Mandate mandate = mandateService.findByJourneyId(demoJourneyId)
                .orElseThrow(() -> new IllegalStateException(
                        "No local mandate is registered for configured demo journey " + demoJourneyId + "."));
        if (!mandate.expiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("The configured demo mandate has expired.");
        }
        return new GuardContext(mandate.journeyId(), mandate.mandateId());
    }

    private StoreSyncCartResponse.Money zero(String currency) { return new StoreSyncCartResponse.Money(currency, "0.00"); }

    private List<StoreSyncCartResponse.ValidationIssue> inventoryIssues(Cart cart) {
        List<StoreSyncCartResponse.ValidationIssue> issues = new ArrayList<>();
        for (String variantId : cart.availabilityIssues()) {
            issues.add(new StoreSyncCartResponse.ValidationIssue("INVENTORY_ISSUE", "BUSINESS_RULE",
                    "Item is unavailable in the requested quantity.", "Please adjust the item quantity or choose another item.", variantId));
        }
        return issues;
    }
}
