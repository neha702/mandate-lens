package com.emeralds.mandatelens.merchant.catalog.model;

import java.math.BigDecimal;

/**
 * Product entry loaded from the merchant catalog source.
 * `availability` describes the stock state; `availableQuantity` is its sellable unit count.
 */
public class Product {

    private String id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String currency;
    private String imageUrl;
    private String availability;
    private int availableQuantity;

    public Product() {
    }

    public Product(
            String id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String currency,
            String imageUrl,
            String availability,
            int availableQuantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.currency = currency;
        this.imageUrl = imageUrl;
        this.availability = availability;
        this.availableQuantity = availableQuantity;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getAvailability() {
        return availability;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
