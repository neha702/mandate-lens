package com.emeralds.mandatelens.merchant.cart.model;

import java.math.BigDecimal;

/**
 * Internal cart line containing catalog values captured when the cart was built.
 * These values are refreshed from the catalog when the cart is replaced.
 *
 * @param variantId catalog variant identifier
 * @param quantity number of units requested
 * @param name product name at evaluation time
 * @param unitPrice catalog unit price at evaluation time
 * @param currency currency for the unit price
 * @param availability catalog availability at evaluation time
 * @param availableQuantity units available at evaluation time
 */
public record CartLine(
        String variantId,
        int quantity,
        String name,
        String category,
        BigDecimal unitPrice,
        String currency,
        String availability,
        int availableQuantity
) { }
