package com.emeralds.mandatelens.merchant.integration.storesync.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Store Sync checkout input carrying the buyer-approved Orders v2 order token. */
public record StoreSyncCheckoutRequest(@NotNull @Valid @JsonProperty("payment_method") PaymentMethod paymentMethod) {
    public record PaymentMethod(@NotBlank String type, @NotBlank String token,
                                @JsonProperty("payer_id") String payerId) { }
}
