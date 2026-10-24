package com.emeralds.mandatelens.agentguard.model;

import java.math.BigDecimal;
import java.util.List;

public record GuardCart(String cartId, List<Item> items, BigDecimal total, String currency,
                        String shippingCountry) {
    public GuardCart { items = List.copyOf(items); }
    public record Item(String productId, String category, int quantity, BigDecimal unitPrice) { }
}
