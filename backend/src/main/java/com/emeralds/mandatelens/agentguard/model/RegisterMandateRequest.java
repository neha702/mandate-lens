package com.emeralds.mandatelens.agentguard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RegisterMandateRequest(String mandateId, String journeyId, String userId, String merchantId,
                                     Authorization authorization, Constraints constraints, Instant expiresAt) {
    public record Authorization(String source, String authorizationReference) { }
    public record Constraints(BigDecimal maxAmount, String currency, List<String> allowedCategories,
                              String shippingCountry) { }
}
