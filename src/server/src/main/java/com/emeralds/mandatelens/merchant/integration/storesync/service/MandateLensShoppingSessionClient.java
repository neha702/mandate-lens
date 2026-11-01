package com.emeralds.mandatelens.merchant.integration.storesync.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

/** Merchant-side client for asking Mandate Lens to start a budget-constrained demo journey. */
@Service
public class MandateLensShoppingSessionClient {
    private final RestClient client;
    private final String merchantId;

    public MandateLensShoppingSessionClient(RestClient.Builder builder,
                                           @Value("${mandatelens.api.base-url:http://127.0.0.1:8081}") String baseUrl,
                                           @Value("${merchant.demo.merchant-id:STRIDEHUB}") String merchantId) {
        this.client = builder.baseUrl(baseUrl).build();
        this.merchantId = merchantId;
    }

    public ShoppingSession start(String sessionId, BigDecimal maxAmount, String currency) {
        return client.post().uri("/api/v1/shopping-sessions")
                .body(new StartRequest(sessionId, merchantId, maxAmount, currency))
                .retrieve().body(ShoppingSession.class);
    }

    private record StartRequest(String sessionId, String merchantId, BigDecimal maxAmount, String currency) { }

    public record ShoppingSession(String sessionId, String journeyId, String mandateId,
                                  BigDecimal maxAmount, String currency, Instant expiresAt,
                                  String constraintStatus, String ap2AuthorizationStatus) { }
}
