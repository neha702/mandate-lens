package com.emeralds.mandatelens.agentpulse.guard;

import com.emeralds.mandatelens.agentpulse.guard.model.GuardCheckRequest;
import com.emeralds.mandatelens.agentpulse.guard.model.GuardCheckResponse;
import com.emeralds.mandatelens.agentpulse.guard.model.GuardDecision;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Demo-only decision logic. Replace with the real AgentGuard team integration. */
@Primary
@Component
public class DemoAgentGuardClient implements AgentGuardClient {
    @Override
    public GuardCheckResponse checkPurchase(GuardCheckRequest request) {
        if (request.shopperLimit() == null || request.latestTotal() == null) {
            return new GuardCheckResponse(GuardDecision.REVIEW, "MISSING_REQUIRED_INFORMATION",
                    "A shopper limit or latest total is missing, so this demo cannot check the purchase.",
                    List.of(), List.of("shopperLimit", "latestTotal"));
        }

        if (isBlank(request.shopperLimit().currency()) || isBlank(request.latestTotal().currency())) {
            return new GuardCheckResponse(GuardDecision.REVIEW, "MISSING_CURRENCY",
                    "Currency is missing, so the totals cannot be safely compared.",
                    List.of(), List.of("currency"));
        }

        if (!request.shopperLimit().currency().equalsIgnoreCase(request.latestTotal().currency())) {
            return new GuardCheckResponse(GuardDecision.REVIEW, "CURRENCY_MISMATCH",
                    "The shopper limit and purchase total use different currencies.",
                    List.of(), List.of("matching currency"));
        }

        if (request.shopperLimit().amount() == null || request.latestTotal().amount() == null) {
            return new GuardCheckResponse(GuardDecision.REVIEW, "MISSING_AMOUNT",
                    "An amount is missing, so the purchase cannot be checked.",
                    List.of(), List.of("shopperLimit.amount", "latestTotal.amount"));
        }

        if (request.shopperLimit().amount().compareTo(request.latestTotal().amount()) < 0) {
            List<String> evidenceIds = request.events().stream()
                    .filter(event -> event.amount() != null
                            && event.amount().compareTo(request.latestTotal().amount()) == 0)
                    .map(GuardCheckRequest.GuardEvidenceEvent::eventId)
                    .toList();
            return new GuardCheckResponse(GuardDecision.BLOCK, "OVER_SHOPPER_LIMIT",
                    "The latest total is above the shopper's stated limit.", evidenceIds, List.of());
        }

        return new GuardCheckResponse(GuardDecision.ALLOW, "WITHIN_SHOPPER_LIMIT",
                "The latest total is within the shopper's stated limit.", List.of(), List.of());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
