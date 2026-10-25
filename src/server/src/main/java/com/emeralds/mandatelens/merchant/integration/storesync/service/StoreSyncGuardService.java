package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests;
import com.emeralds.mandatelens.agentpulse.orchestration.MerchantOperationService;
import com.emeralds.mandatelens.mandate.model.Mandate;
import com.emeralds.mandatelens.mandate.service.MandateService;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bridges the local Store Sync simulator to the merchant operation check/result flow. */
@Service
public class StoreSyncGuardService {
    private final MerchantOperationService operations;
    private final MandateService mandates;

    public StoreSyncGuardService(MerchantOperationService operations, MandateService mandates) {
        this.operations = operations;
        this.mandates = mandates;
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
        String merchantId = merchantFor(context);
        operations.result(operationId, new MerchantOperationRequests.Result(
                "evt-store-sync-" + UUID.randomUUID(), merchantId, context.journeyId(), cartId,
                eventType, outcome, Instant.now(), merchantReference,
                new MerchantOperationRequests.ResultDetails(total, currency, failureCode, message,
                        paypalOrderId, paypalCaptureId, changedItems)));
    }

    private String check(String cartId, GuardContext context, String action, Cart current,
                         Cart proposed, String proposedCountry) {
        if (proposedCountry == null || proposedCountry.isBlank()) {
            throw new GuardIntegrationException("Shipping country is required for AgentGuard evaluation.");
        }
        String merchantId = merchantFor(context);
        var response = operations.check(new MerchantOperationRequests.Check(
                merchantId, context.journeyId(), cartId, action,
                "storesync:" + cartId + ":" + UUID.randomUUID(),
                toRequestCart(current), toRequestCart(proposed), proposedCountry));
        if (response.decision() != GuardDecision.ALLOW) {
            String reasons = response.reasons().stream()
                    .map(reason -> reason.code() + ": " + reason.message())
                    .reduce((left, right) -> left + "; " + right).orElse("No reason provided.");
            throw new GuardDecisionDeniedException(response.decision(), reasons);
        }
        return response.operationId();
    }

    private String merchantFor(GuardContext context) {
        if (context == null || context.journeyId() == null || context.journeyId().isBlank()
                || context.mandateId() == null || context.mandateId().isBlank()) {
            throw new GuardIntegrationException("This cart has no journey-to-mandate mapping.");
        }
        Mandate mandate = mandates.findByJourneyId(context.journeyId())
                .orElseThrow(() -> new GuardIntegrationException("No active mandate is linked to this cart."));
        if (!mandate.mandateId().equals(context.mandateId())) {
            throw new GuardIntegrationException("The cart mandate mapping is no longer valid.");
        }
        return mandate.merchantId();
    }

    private MerchantOperationRequests.Cart toRequestCart(Cart cart) {
        List<MerchantOperationRequests.Item> items = cart.items().stream().map(this::toRequestItem).toList();
        return new MerchantOperationRequests.Cart(cart.currency(), items, cart.subtotal());
    }

    private MerchantOperationRequests.Item toRequestItem(CartLine line) {
        return new MerchantOperationRequests.Item(line.variantId(), line.category(),
                line.quantity(), line.unitPrice());
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
