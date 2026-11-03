package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Calls Mandate Lens over HTTP for decisions and reports the merchant's actual result. */
@Service
public class StoreSyncGuardService {
    private final RestClient client;
    private final String merchantId;
    private final String baseUrl;

    public StoreSyncGuardService(RestClient.Builder builder,
                                 @Value("${mandatelens.api.base-url:http://127.0.0.1:8081}") String baseUrl,
                                 @Value("${merchant.demo.merchant-id:STRIDEHUB}") String merchantId) {
        this.baseUrl = baseUrl;
        this.client = builder.baseUrl(baseUrl).build();
        this.merchantId = merchantId;
    }

    public String checkCartUpdate(String cartId, GuardContext context, Cart current,
                                  Cart proposed, String proposedCountry) {
        return check(cartId, context, "UPDATE_CART", current, proposed, proposedCountry);
    }

    public String checkCartAdd(String cartId, GuardContext context, Cart proposed, String proposedCountry) {
        Cart empty = new Cart(cartId, context.journeyId(), List.of(), BigDecimal.ZERO, proposed.currency(), List.of());
        return check(cartId, context, "ADD_ITEM", empty, proposed, proposedCountry);
    }

    public String checkCheckout(String cartId, GuardContext context, Cart cart, String shippingCountry) {
        return check(cartId, context, "CHECKOUT", cart, cart, shippingCountry);
    }

    public String newResultOnlyOperationId() {
        return "op-" + UUID.randomUUID();
    }

    public void reportResult(String operationId, GuardContext context, String cartId,
                             String eventType, String outcome, BigDecimal total, String currency,
                             String merchantReference, String failureCode, String message,
                             String paypalOrderId, String paypalCaptureId,
                             List<MerchantOperationRequests.ChangedItem> changedItems) {
        validateContext(context);
        var request = new MerchantOperationRequests.Result(
                "evt-store-sync-" + UUID.randomUUID(), merchantId, context.journeyId(), cartId,
                eventType, outcome, Instant.now(), merchantReference,
                new MerchantOperationRequests.ResultDetails(total, currency, failureCode, message,
                        paypalOrderId, paypalCaptureId, changedItems));
        try {
            client.post().uri("/api/v1/merchant-operations/{operationId}/result", operationId)
                    .body(request).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable("Could not report the merchant result to Mandate Lens.", exception);
        }
    }

    private String check(String cartId, GuardContext context, String action, Cart current,
                         Cart proposed, String proposedCountry) {
        if (proposedCountry == null || proposedCountry.isBlank()) {
            throw new GuardIntegrationException("Shipping country is required for the Mandate Lens check.");
        }
        validateContext(context);
        var request = new MerchantOperationRequests.Check(
                merchantId, context.journeyId(), cartId, action,
                "storesync:" + cartId + ":" + UUID.randomUUID(),
                toRequestCart(current), toRequestCart(proposed), proposedCountry);
        JsonNode response;
        try {
            response = client.post().uri("/api/v1/merchant-operations/check")
                    .body(request).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw unavailable("Mandate Lens could not evaluate this cart action.", exception);
        }
        if (response == null || response.path("operationId").asText().isBlank()) {
            throw new GuardIntegrationException("Mandate Lens returned an incomplete decision.");
        }
        GuardDecision decision;
        try {
            decision = GuardDecision.valueOf(response.path("decision").asText());
        } catch (IllegalArgumentException exception) {
            throw new GuardIntegrationException("Mandate Lens returned an unknown decision.");
        }
        if (decision != GuardDecision.ALLOW) {
            String reasons = response.path("reasons").findValuesAsText("message").stream()
                    .reduce((left, right) -> left + "; " + right).orElse("No reason provided.");
            throw new GuardDecisionDeniedException(decision, reasons);
        }
        return response.path("operationId").asText();
    }

    private void validateContext(GuardContext context) {
        if (context == null || context.journeyId() == null || context.journeyId().isBlank()) {
            throw new GuardIntegrationException("This cart has no Mandate Lens journey mapping.");
        }
    }

    private MerchantOperationRequests.Cart toRequestCart(Cart cart) {
        List<MerchantOperationRequests.Item> items = cart.items().stream().map(this::toRequestItem).toList();
        return new MerchantOperationRequests.Cart(cart.currency(), items, cart.subtotal());
    }

    private MerchantOperationRequests.Item toRequestItem(CartLine line) {
        return new MerchantOperationRequests.Item(line.variantId(), line.category(),
                line.quantity(), line.unitPrice());
    }

    private GuardIntegrationException unavailable(String message, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return new GuardIntegrationException(message + " HTTP " + responseException.getStatusCode().value() + ".");
        }
        return new GuardIntegrationException(message + " Is Mandate Lens running at " + baseUrl + "?");
    }

    public static class GuardIntegrationException extends RuntimeException {
        public GuardIntegrationException(String message) { super(message); }
    }

    public static final class GuardDecisionDeniedException extends GuardIntegrationException {
        private final GuardDecision decision;
        public GuardDecisionDeniedException(GuardDecision decision, String reasons) {
            super("AgentGuard decision " + decision + ": " + reasons);
            this.decision = decision;
        }
        public GuardDecision decision() { return decision; }
    }
}
