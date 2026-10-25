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
        String authorizationReference,
        BigDecimal maxAmount,
        String currency,
        List<String> allowedCategories,
        List<String> allowedProductIds,
        String shippingCountry,
        Instant createdAt,
        Instant expiresAt,
        Source source,
        Ap2AuthorizationEnvelope authorizationEvidence,
        Instant agentGuardRegisteredAt
) {
    public Mandate {
        allowedCategories = allowedCategories == null ? List.of() : List.copyOf(allowedCategories);
        allowedProductIds = allowedProductIds == null ? List.of() : List.copyOf(allowedProductIds);
    }

    public enum Source {
        SIMULATED_AP2
    }
}
