package com.emeralds.mandatelens.merchant.cart.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Internal cart aggregate; external Store Sync fields are mapped separately.
 *
 * @param id locally generated cart identifier
 * @param items immutable snapshot of the cart lines
 * @param subtotal sum of unit prices multiplied by quantities
 * @param currency currency shared by every cart line
 * @param availabilityIssues variant IDs that are out of stock or lack enough units
 */
public record Cart(
        String id,
        String journeyId,
        List<CartLine> items,
        BigDecimal subtotal,
        String currency,
        List<String> availabilityIssues
) {
    public Cart {
        items = List.copyOf(items);
        availabilityIssues = List.copyOf(availabilityIssues);
    }
}
