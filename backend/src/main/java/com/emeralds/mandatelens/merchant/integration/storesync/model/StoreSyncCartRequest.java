package com.emeralds.mandatelens.merchant.integration.storesync.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Store Sync request DTO. Catalog prices are always resolved server-side. */
public record StoreSyncCartRequest(
        @NotEmpty List<@Valid Item> items,
        @Valid Customer customer,
        @JsonProperty("shipping_address") @Valid Address shippingAddress,
        @JsonProperty("billing_address") @Valid Address billingAddress,
        @JsonProperty("payment_method") @Valid PaymentMethod paymentMethod) {

    public record Item(@JsonProperty("variant_id") String variantId,
                       @JsonProperty("item_id") String itemId,
                       @Min(1) int quantity) {
        public Item {
            if (isBlank(variantId) && isBlank(itemId)) {
                throw new IllegalArgumentException("Each item must include variant_id or item_id.");
            }
        }

        /** The catalog uses the PayPal variant identifier; item_id is the documented legacy fallback. */
        public String catalogVariantId() {
            return isBlank(variantId) ? itemId : variantId;
        }
    }

    public record Customer(@Valid Name name,
                           @Valid Phone phone,
                           @JsonProperty("email_address") String emailAddress) { }

    public record Name(@JsonProperty("given_name") String givenName,
                       String surname) { }

    public record Phone(@JsonProperty("country_code") @NotBlank String countryCode,
                        @JsonProperty("national_number") @NotBlank String nationalNumber,
                        @JsonProperty("extension_number") String extensionNumber) { }

    public record Address(@JsonProperty("address_line_1") String addressLine1,
                          @JsonProperty("address_line_2") String addressLine2,
                          @JsonProperty("admin_area_2") String adminArea2,
                          @JsonProperty("admin_area_1") String adminArea1,
                          @JsonProperty("postal_code") String postalCode,
                          @JsonProperty("country_code") @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode) { }

    public record PaymentMethod(@NotBlank String type,
                                String token,
                                @JsonProperty("payer_id") String payerId,
                                @JsonProperty("approval_url") String approvalUrl) { }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
