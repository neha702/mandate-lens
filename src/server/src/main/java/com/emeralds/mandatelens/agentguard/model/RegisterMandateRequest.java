package com.emeralds.mandatelens.agentguard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RegisterMandateRequest(String mandateId, String journeyId, String userId, String merchantId,
                                     Authorization authorization, Constraints constraints, Instant expiresAt) {
    public record Authorization(String source, String authorizationReference, String protocolVersion,
                                String envelopeHash, String verificationStatus) { }
    public record Constraints(BigDecimal maxAmount, String currency, List<String> allowedCategories,
                              String shippingCountry, List<String> allowedProductIds) {
        public Constraints {
            allowedCategories = allowedCategories == null ? List.of() : List.copyOf(allowedCategories);
            allowedProductIds = allowedProductIds == null ? List.of() : List.copyOf(allowedProductIds);
        }
    }
}
