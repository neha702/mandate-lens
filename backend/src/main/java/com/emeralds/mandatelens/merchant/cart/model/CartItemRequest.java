package com.emeralds.mandatelens.merchant.cart.model;

/**
 * Input to the internal cart service. This is not a PayPal request DTO.
 *
 * @param variantId catalog variant identifier
 * @param quantity number of units requested
 */
public record CartItemRequest(String variantId, int quantity) { }
