package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-side PayPal Orders v2 calls used by the Store Sync simulator. */
@Service
public class PayPalOrdersClient {
    private static final Logger logger = LoggerFactory.getLogger(PayPalOrdersClient.class);
    private final RestClient paypal;
    private final String clientId;
    private final String clientSecret;
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalOrdersClient(
            @Value("${paypal.orders.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
            @Value("${paypal.orders.client-id:}") String clientId,
            @Value("${paypal.orders.client-secret:}") String clientSecret) {
        this.paypal = RestClient.builder().baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Creates an Orders v2 order for the catalog-priced cart total. */
    public OrderResult createOrder(String cartId, Map<String, Object> orderBody) {
        requireCredentials();
        OrderResponse response = withRateLimitRetry(() -> paypal.post().uri("/v2/checkout/orders")
                .header("Authorization", "Bearer " + accessToken())
                .header("PayPal-Request-Id", "CART-ORDER-" + cartId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody).retrieve().body(OrderResponse.class));
        if (response == null || response.id() == null) {
            throw new PayPalGatewayException("PayPal returned an empty order response.");
        }
        String approvalUrl = response.links() == null ? null : response.links().stream()
                .filter(link -> "approve".equalsIgnoreCase(link.rel()))
                .map(Link::href).findFirst().orElse(null);
        return new OrderResult(response.id(), approvalUrl);
    }

    /** Updates the amount, line items, and shipping address for a replaced cart. */
    public void updateOrder(String cartId, int revision, String orderId, Map<String, Object> amount,
                            List<Map<String, Object>> items, Map<String, Object> shipping,
                            boolean removeShipping) {
        requireCredentials();
        List<Map<String, Object>> patch = new java.util.ArrayList<>();
        patch.add(Map.of("op", "replace", "path", "/purchase_units/@reference_id=='default'/amount", "value", amount));
        patch.add(Map.of("op", "replace", "path", "/purchase_units/@reference_id=='default'/items", "value", items));
        if (shipping != null) {
            patch.add(Map.of("op", "add", "path", "/purchase_units/@reference_id=='default'/shipping", "value", shipping));
        } else if (removeShipping) {
            patch.add(Map.of("op", "remove", "path", "/purchase_units/@reference_id=='default'/shipping"));
        }
        withRateLimitRetry(() -> paypal.patch().uri("/v2/checkout/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + accessToken())
                .header("PayPal-Request-Id", "CART-UPDATE-" + cartId + "-" + revision)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patch).retrieve().toBodilessEntity());
    }

    /** Captures only when called by the checkout orchestration after an AgentGuard ALLOW decision. */
    public CaptureResult captureOrder(String cartId, String orderId) {
        requireCredentials();
        CaptureResponse response = withRateLimitRetry(() -> paypal.post()
                .uri("/v2/checkout/orders/{orderId}/capture", orderId)
                .header("Authorization", "Bearer " + accessToken())
                .header("PayPal-Request-Id", "CART-CAPTURE-" + cartId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve().body(CaptureResponse.class));
        Capture capture = response == null || response.purchaseUnits() == null ? null
                : response.purchaseUnits().stream().filter(unit -> unit.payments() != null
                        && unit.payments().captures() != null)
                .flatMap(unit -> unit.payments().captures().stream()).findFirst().orElse(null);
        if (capture == null || capture.id() == null || !"COMPLETED".equalsIgnoreCase(capture.status())) {
            throw new PayPalGatewayException("PayPal did not return a completed capture.");
        }
        return new CaptureResult(capture.status(), capture.id());
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        String oauthRequestId = UUID.randomUUID().toString();
        TokenResponse token = withRateLimitRetry(() -> paypal.post().uri("/v1/oauth2/token")
                .header("PayPal-Request-Id", oauthRequestId)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(TokenResponse.class));
        if (token == null || token.accessToken() == null || token.expiresIn() == null) {
            throw new PayPalGatewayException("PayPal OAuth returned an invalid token response.");
        }
        accessToken = token.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn());
        return accessToken;
    }

    private <T> T withRateLimitRetry(Supplier<T> request) {
        for (int attempt = 0; ; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 429 || attempt >= 3) throw gatewayError(exception);
                long waitMillis = retryDelayMillis(exception, attempt);
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new PayPalGatewayException("Interrupted while waiting to retry a PayPal request.", 429, interrupted);
                }
            }
        }
    }

    private long retryDelayMillis(RestClientResponseException exception, int attempt) {
        String retryAfter = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.min(10_000L, Math.max(250L, Long.parseLong(retryAfter) * 1_000L));
            } catch (NumberFormatException ignored) {
                // Fall back to bounded exponential backoff when the header is not a number.
            }
        }
        return Math.min(8_000L, 500L * (1L << attempt));
    }

    private void requireCredentials() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new PayPalNotConfiguredException();
        }
    }

    private PayPalGatewayException gatewayError(RestClientResponseException exception) {
        String debugId = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst("Paypal-Debug-Id");
        logger.warn("PayPal API request failed: HTTP {}, debug_id={}", exception.getStatusCode().value(), debugId);
        return new PayPalGatewayException("PayPal API returned HTTP " + exception.getStatusCode().value()
                + (debugId == null ? "" : " (debug_id=" + debugId + ")"),
                exception.getStatusCode().value(), exception);
    }

    public record OrderResult(String orderId, String approvalUrl) { }
    public record CaptureResult(String status, String captureId) { }
    public record CaptureResponse(String status,
                                  @JsonProperty("purchase_units") List<CapturePurchaseUnit> purchaseUnits) { }
    public record CapturePurchaseUnit(CapturePayments payments) { }
    public record CapturePayments(List<Capture> captures) { }
    public record Capture(String id, String status) { }

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("expires_in") Long expiresIn) { }
    public record OrderResponse(String id, List<Link> links) { }
    public record Link(String rel, String href) { }
    public static class PayPalNotConfiguredException extends RuntimeException {
        public PayPalNotConfiguredException() {
            super("PayPal Orders v2 is not configured; set PAYPAL_ORDERS_CLIENT_ID and PAYPAL_ORDERS_CLIENT_SECRET.");
        }
    }

    public static class PayPalGatewayException extends RuntimeException {
        private final int httpStatus;
        public PayPalGatewayException(String message) { super(message); this.httpStatus = 0; }
        public PayPalGatewayException(String message, int httpStatus, Throwable cause) {
            super(message, cause);
            this.httpStatus = httpStatus;
        }
        public int httpStatus() { return httpStatus; }
    }
}
