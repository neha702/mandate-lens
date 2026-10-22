package com.emeralds.mandatelens.mandate.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Internal authorization evidence; demo records are explicitly marked as simulated. */
public record Mandate(
        String mandateId,
        String journeyId,
        String userId,
        String merchantId,
        BigDecimal maxAmount,
        String currency,
        List<String> allowedCategories,
        String shippingCountry,
        Instant createdAt,
        Instant expiresAt,
        Source source
) {
    public Mandate {
        allowedCategories = allowedCategories == null ? List.of() : List.copyOf(allowedCategories);
    }

    public enum Source {
        SIMULATED_AP2
    }
}
