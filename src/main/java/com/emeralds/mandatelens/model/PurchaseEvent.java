package com.emeralds.mandatelens.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A normalized, source-labelled event in a purchase journey. */
public record PurchaseEvent(
        String id,
        String journeyId,
        Instant occurredAt,
        String type,
        String source,
        BigDecimal amount,
        String currency,
        String details
) { }
