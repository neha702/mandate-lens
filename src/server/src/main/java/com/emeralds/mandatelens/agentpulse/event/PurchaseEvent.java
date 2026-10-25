package com.emeralds.mandatelens.agentpulse.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** A normalized, source-labelled event in a purchase journey. */
public record PurchaseEvent(
        String id,
        String journeyId,
        Instant occurredAt,
        String type,
        String source,
        BigDecimal amount,
        String currency,
        String details,
        Map<String, Object> structuredDetails
) {
    public PurchaseEvent(String id, String journeyId, Instant occurredAt, String type, String source,
                         BigDecimal amount, String currency, String details) {
        this(id, journeyId, occurredAt, type, source, amount, currency, details, Map.of());
    }

    public PurchaseEvent {
        structuredDetails = structuredDetails == null ? Map.of() : Map.copyOf(structuredDetails);
    }
}
