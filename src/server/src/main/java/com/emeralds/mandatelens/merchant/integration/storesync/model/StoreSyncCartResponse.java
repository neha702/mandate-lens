package com.emeralds.mandatelens.merchant.integration.storesync.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest.Address;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest.Customer;

import java.util.List;

/** Store Sync cart response assembled from catalog-priced merchant state. */
public record StoreSyncCartResponse(
        String id,
        String status,
        @JsonProperty("validation_status") String validationStatus,
        @JsonProperty("validation_issues") List<ValidationIssue> validationIssues,
        List<Item> items,
        Totals totals,
        @JsonProperty("customer") Customer customer,
        @JsonProperty("shipping_address") Address shippingAddress,
        @JsonProperty("billing_address") Address billingAddress,
        @JsonProperty("payment_method") PaymentMethod paymentMethod,
        @JsonProperty("payment_confirmation") PaymentConfirmation paymentConfirmation
) {
    public record Money(@JsonProperty("currency_code") String currencyCode, String value) { }
    public record Item(@JsonProperty("variant_id") String variantId, int quantity, String name,
                       @JsonProperty("unit_amount") Money unitAmount,
                       @JsonProperty("item_total") Money itemTotal) { }
    public record Totals(Money subtotal, Money total, Money shipping, Money tax) { }
    public record PaymentMethod(String type, String token,
                                @JsonProperty("payer_id") String payerId,
                                @JsonProperty("approval_url") String approvalUrl) { }
    public record ValidationIssue(String code, String type, String message,
                                  @JsonProperty("user_message") String userMessage,
                                  @JsonProperty("variant_id") String variantId) { }
    public record PaymentConfirmation(@JsonProperty("merchant_order_number") String merchantOrderNumber,
                                      @JsonProperty("order_review_page") String orderReviewPage) { }
}
