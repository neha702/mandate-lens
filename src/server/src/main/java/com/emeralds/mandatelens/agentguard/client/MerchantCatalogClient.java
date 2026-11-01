package com.emeralds.mandatelens.agentguard.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/** Reads merchant-owned catalog facts over HTTP instead of reading its database directly. */
@Component
public class MerchantCatalogClient {
    private static final ParameterizedTypeReference<List<ProductSnapshot>> PRODUCT_LIST =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public MerchantCatalogClient(RestClient.Builder builder,
                                 @Value("${merchant.api.base-url:http://127.0.0.1:8080}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public List<ProductSnapshot> getProducts() {
        try {
            List<ProductSnapshot> products = client.get().uri("/api/products")
                    .retrieve().body(PRODUCT_LIST);
            if (products == null) throw new MerchantCatalogUnavailableException("Merchant catalog response was empty.");
            return products;
        } catch (RestClientException exception) {
            throw new MerchantCatalogUnavailableException("Could not read the merchant catalog API.", exception);
        }
    }

    public record ProductSnapshot(String id, String category, BigDecimal price, String currency) { }

    public static class MerchantCatalogUnavailableException extends RuntimeException {
        public MerchantCatalogUnavailableException(String message) { super(message); }
        public MerchantCatalogUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
