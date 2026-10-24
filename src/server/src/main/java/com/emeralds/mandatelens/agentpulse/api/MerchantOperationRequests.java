package com.emeralds.mandatelens.agentpulse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MerchantOperationRequests {
    private MerchantOperationRequests() { }

    public record Check(
            @NotBlank String merchantId,
            @NotBlank String journeyId,
            @NotBlank String cartId,
            @NotBlank String action,
            @NotBlank String idempotencyKey,
            @NotNull @Valid Cart currentCart,
            @NotNull @Valid Cart proposedCart,
            @NotBlank String shippingCountry) { }

    public record Cart(@NotBlank String currency, @NotNull List<@Valid Item> items,
                       @NotNull BigDecimal total) {
        public Cart { items = items == null ? List.of() : List.copyOf(items); }
    }

    public record Item(@NotBlank String productId, @NotBlank String category,
                       int quantity, @NotNull BigDecimal unitPrice) { }

    public record Result(
            @NotBlank String eventId,
            @NotBlank String merchantId,
            @NotBlank String journeyId,
            @NotBlank String cartId,
            @NotBlank String eventType,
            @NotBlank String outcome,
            @NotNull Instant occurredAt,
            String merchantReference,
            @NotNull @Valid ResultDetails details) { }

    public record ResultDetails(BigDecimal total, String currency, String failureCode,
                                String message, String paypalOrderId, String paypalCaptureId) { }
}
