package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(StoreSyncGuardService.class);
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

    public CheckoutGuardResult checkCheckoutDetailed(String cartId, GuardContext context, Cart cart,
                                                       String shippingCountry) {
        JsonNode response = requestDecision(cartId, context, "CHECKOUT", cart, cart, shippingCountry);
        String operationId = response.path("operationId").asText();
        GuardDecision decision;
        try {
            decision = GuardDecision.valueOf(response.path("decision").asText());
        } catch (IllegalArgumentException exception) {
            logger.error("Mandate Lens checkout response had an unknown decision cartId={} journeyId={} operationId={} decision={}",
                    cartId, context.journeyId(), operationId, response.path("decision").asText());
            throw new GuardIntegrationException("Mandate Lens returned an unknown checkout decision.");
        }
        logger.info("Mandate Lens checkout decision parsed cartId={} journeyId={} operationId={} decision={} decisionId={} reasonCount={} ruleCount={}",
                cartId, context.journeyId(), operationId, decision, response.path("decisionId").asText(null),
                response.path("reasons").isArray() ? response.path("reasons").size() : 0,
                response.path("ruleResults").isArray() ? response.path("ruleResults").size() : 0);
        return new CheckoutGuardResult(operationId, decision.name(), response);
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
        JsonNode response = requestDecision(cartId, context, action, current, proposed, proposedCountry);
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

    private JsonNode requestDecision(String cartId, GuardContext context, String action, Cart current,
                                     Cart proposed, String proposedCountry) {
        if (proposedCountry == null || proposedCountry.isBlank()) {
            logger.warn("Mandate Lens decision request rejected action={} cartId={} reason=SHIPPING_COUNTRY_MISSING",
                    action, cartId);
            throw new GuardIntegrationException("Shipping country is required for the Mandate Lens check.");
        }
        try {
            validateContext(context);
        } catch (GuardIntegrationException exception) {
            logger.warn("Mandate Lens decision request rejected action={} cartId={} reason=JOURNEY_CONTEXT_MISSING",
                    action, cartId);
            throw exception;
        }
        String correlationId = "storesync:" + cartId + ":" + UUID.randomUUID();
        var request = new MerchantOperationRequests.Check(
                merchantId, context.journeyId(), cartId, action,
                correlationId,
                toRequestCart(current), toRequestCart(proposed), proposedCountry);
        long startedAt = System.nanoTime();
        logger.info("Mandate Lens decision request started action={} merchantId={} journeyId={} cartId={} correlationId={} currentTotal={} proposedTotal={} currency={} currentItems={} proposedItems={} shippingCountry={}",
                action, merchantId, context.journeyId(), cartId, correlationId, current.subtotal(), proposed.subtotal(),
                proposed.currency(), current.items().size(), proposed.items().size(), proposedCountry);
        JsonNode response;
        try {
            response = client.post().uri("/api/v1/merchant-operations/check")
                    .body(request).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            Integer httpStatus = exception instanceof RestClientResponseException responseException
                    ? responseException.getStatusCode().value() : null;
            logger.error("Mandate Lens decision request failed action={} journeyId={} cartId={} correlationId={} httpStatus={} durationMs={} errorType={}",
                    action, context.journeyId(), cartId, correlationId, httpStatus,
                    (System.nanoTime() - startedAt) / 1_000_000, exception.getClass().getSimpleName(), exception);
            throw unavailable("Mandate Lens could not evaluate this cart action.", exception);
        }
        if (response == null || response.path("operationId").asText().isBlank()) {
            logger.error("Mandate Lens decision response incomplete action={} journeyId={} cartId={} correlationId={} durationMs={} responseWasNull={}",
                    action, context.journeyId(), cartId, correlationId,
                    (System.nanoTime() - startedAt) / 1_000_000, response == null);
            throw new GuardIntegrationException("Mandate Lens returned an incomplete decision.");
        }
        logger.info("Mandate Lens decision response received action={} journeyId={} cartId={} correlationId={} operationId={} decisionId={} decision={} reasonCount={} ruleCount={} durationMs={}",
                action, context.journeyId(), cartId, correlationId, response.path("operationId").asText(),
                response.path("decisionId").asText(null), response.path("decision").asText("UNKNOWN"),
                response.path("reasons").isArray() ? response.path("reasons").size() : 0,
                response.path("ruleResults").isArray() ? response.path("ruleResults").size() : 0,
                (System.nanoTime() - startedAt) / 1_000_000);
        return response;
    }

    public record CheckoutGuardResult(String operationId, String decision, JsonNode details) { }

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
