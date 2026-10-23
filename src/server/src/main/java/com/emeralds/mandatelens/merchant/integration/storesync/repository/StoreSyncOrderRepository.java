package com.emeralds.mandatelens.merchant.integration.storesync.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** PostgreSQL persistence for Store Sync adapter state associated with canonical merchant carts. */
@Repository
public class StoreSyncOrderRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StoreSyncOrderRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<OrderState> findOrder(String cartId) { return find(cartId, "order_state", OrderState.class); }
    @Transactional
    public void saveOrder(String cartId, OrderState order) {
        jdbc.update("""
                INSERT INTO merchant.orders
                (order_id, cart_id, status, currency, subtotal, shipping_amount, tax_amount, total)
                SELECT ?, cart_id, 'PENDING', currency, subtotal, shipping_amount, tax_amount, total
                FROM merchant.carts WHERE cart_id = ?
                ON CONFLICT (order_id) DO UPDATE SET updated_at = NOW()
                """, order.orderId(), cartId);
        jdbc.update("""
                INSERT INTO merchant.payments (order_id, provider, provider_order_id, status, amount, currency)
                SELECT ?, 'PAYPAL', ?, 'PENDING', total, currency FROM merchant.carts WHERE cart_id = ?
                ON CONFLICT (provider_order_id) DO UPDATE SET amount = EXCLUDED.amount,
                    currency = EXCLUDED.currency, updated_at = NOW()
                """, order.orderId(), order.orderId(), cartId);
        save(cartId, "order_state", order);
    }
    public Optional<CartDetails> findCartDetails(String cartId) { return find(cartId, "cart_details", CartDetails.class); }
    public void saveCartDetails(String cartId, CartDetails details) { save(cartId, "cart_details", details); }
    public Optional<GuardContext> findGuardContext(String cartId) { return find(cartId, "guard_context", GuardContext.class); }
    public void saveGuardContext(String cartId, GuardContext context) { save(cartId, "guard_context", context); }
    public Optional<StoreSyncCartResponse> findCompletedCart(String cartId) {
        return find(cartId, "completed_response", StoreSyncCartResponse.class);
    }
    @Transactional
    public void saveCompletedCart(String cartId, StoreSyncCartResponse response, String captureId) {
        String orderId = response.paymentConfirmation() == null
                ? null : response.paymentConfirmation().merchantOrderNumber();
        jdbc.update("UPDATE merchant.carts SET status = 'COMPLETED', updated_at = NOW() WHERE cart_id = ?", cartId);
        if (orderId != null) {
            jdbc.update("UPDATE merchant.orders SET status = 'COMPLETED', updated_at = NOW() WHERE order_id = ?", orderId);
            jdbc.update("""
                    UPDATE merchant.payments SET status = 'CAPTURED', provider_capture_id = ?, updated_at = NOW()
                    WHERE provider_order_id = ?
                    """, captureId, orderId);
        }
        save(cartId, "completed_response", response);
    }

    private <T> Optional<T> find(String cartId, String column, Class<T> type) {
        // Column names are selected only by the fixed internal methods above.
        return jdbc.query("SELECT " + column + "::text FROM merchant.store_sync_cart_state WHERE cart_id = ?",
                rs -> rs.next() ? Optional.ofNullable(read(rs.getString(1), type)) : Optional.empty(), cartId);
    }

    private void save(String cartId, String column, Object value) {
        String json = write(value);
        int updated = jdbc.update("UPDATE merchant.store_sync_cart_state SET " + column + " = ?::jsonb, updated_at = NOW() WHERE cart_id = ?",
                json, cartId);
        if (updated == 0) {
            jdbc.update("INSERT INTO merchant.store_sync_cart_state (cart_id, " + column + ") VALUES (?, ?::jsonb)",
                    cartId, json);
        }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Could not encode Store Sync state", exception); }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read Store Sync state", exception); }
    }

    public record CartDetails(StoreSyncCartRequest.Customer customer,
                              StoreSyncCartRequest.Address shippingAddress,
                              StoreSyncCartRequest.Address billingAddress) { }
    public record GuardContext(String journeyId, String mandateId) { }
    public record OrderState(String orderId, int revision, String approvalUrl) { }
}
