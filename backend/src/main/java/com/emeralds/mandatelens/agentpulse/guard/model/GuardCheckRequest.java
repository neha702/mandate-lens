package com.emeralds.mandatelens.agentpulse.guard.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Draft shared format for asking AgentGuard to check a purchase.
 * Confirm or change these fields with the AgentGuard team before treating them as final.
 */
public record GuardCheckRequest(
        String journeyId,
        Money shopperLimit,
        Money latestTotal,
        List<GuardEvidenceEvent> events
) {
    public GuardCheckRequest {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public record Money(BigDecimal amount, String currency, String source) { }

    public record GuardEvidenceEvent(
            String eventId,
            String type,
            BigDecimal amount,
            String currency,
            String source
    ) { }
}
