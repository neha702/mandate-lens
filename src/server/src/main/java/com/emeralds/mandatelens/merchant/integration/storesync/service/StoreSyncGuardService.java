package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.emeralds.mandatelens.agentguard.client.AgentGuardClient;
import com.emeralds.mandatelens.agentguard.model.EvaluateCartRequest;
import com.emeralds.mandatelens.agentguard.model.EvaluateCheckoutRequest;
import com.emeralds.mandatelens.agentguard.model.GuardCart;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Maps Store Sync merchant cart facts to AgentGuard and applies the returned decision. */
@Service
public class StoreSyncGuardService {
    private final AgentGuardClient client;
    private final boolean enabled;

    public StoreSyncGuardService(AgentGuardClient client,
                                 @Value("${agentguard.enabled:true}") boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public GuardDecisionResponse evaluateCart(String cartId, GuardContext context,
                                               Cart current, String currentCountry,
                                               Cart proposed, String proposedCountry) {
        // Only the loopback-only storesync-sandbox-test profile skips the Guard call.
        if (!enabled) return null;
        requireContext(context);
        GuardDecisionResponse response = client.evaluateCart(new EvaluateCartRequest(context.journeyId(), context.mandateId(),
                "UPDATE_CART", toGuardCart(cartId, current, currentCountry),
                toGuardCart(cartId, proposed, proposedCountry)));
        requireAllow(response);
        return response;
    }

    public GuardDecisionResponse evaluateCheckout(String cartId, GuardContext context,
                                                   Cart cart, String shippingCountry) {
        // Only the loopback-only storesync-sandbox-test profile skips the Guard call.
        if (!enabled) return null;
        requireContext(context);
        GuardDecisionResponse response = client.evaluateCheckout(new EvaluateCheckoutRequest(context.journeyId(),
                context.mandateId(), toGuardCart(cartId, cart, shippingCountry)));
        requireAllow(response);
        return response;
    }

    private GuardCart toGuardCart(String cartId, Cart cart, String shippingCountry) {
        if (shippingCountry == null || shippingCountry.isBlank()) {
            throw new GuardIntegrationException("Shipping country is required for AgentGuard evaluation.");
        }
        List<GuardCart.Item> items = cart.items().stream().map(this::toGuardItem).toList();
        return new GuardCart(cartId, items, cart.subtotal(), cart.currency(), shippingCountry);
    }

    private GuardCart.Item toGuardItem(CartLine item) {
        if (item.category() == null || item.category().isBlank()) {
            throw new GuardIntegrationException("Catalog category is required for AgentGuard evaluation.");
        }
        return new GuardCart.Item(item.variantId(), item.category(), item.quantity(), item.unitPrice());
    }

    private void requireContext(GuardContext context) {
        if (context == null || context.journeyId() == null || context.journeyId().isBlank()
                || context.mandateId() == null || context.mandateId().isBlank()) {
            throw new GuardIntegrationException("This cart has no journey-to-mandate mapping.");
        }
    }

    private void requireAllow(GuardDecisionResponse response) {
        if (response == null || response.decisionId() == null || response.decisionId().isBlank()
                || response.decision() == null) {
            throw new GuardIntegrationException("AgentGuard returned an invalid decision response.");
        }
        if (response.decision() != GuardDecision.ALLOW) {
            String reasons = response.reasons().stream()
                    .map(reason -> reason.code() + ": " + reason.message())
                    .reduce((left, right) -> left + "; " + right).orElse("No reason provided.");
            throw new GuardDecisionDeniedException(response.decision(), reasons);
        }
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
