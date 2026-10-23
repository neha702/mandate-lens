package com.emeralds.mandatelens.merchant.catalog.controller;

import com.emeralds.mandatelens.merchant.catalog.model.Product;
import com.emeralds.mandatelens.merchant.catalog.service.ProductService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;

/** Exposes the merchant catalog through the storefront catalog endpoint. */
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "https://mandate-lens-store.onrender.com"
        }
)
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Returns the products currently stored in the merchant catalog database. */
    @GetMapping
    public List<Product> getProducts() throws IOException {
        return productService.getProducts();
    }
}
