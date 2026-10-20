package com.emeralds.mandatelens.storesync.controller;

import com.emeralds.mandatelens.storesync.model.Product;
import com.emeralds.mandatelens.storesync.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getProducts() throws IOException {
        return productService.getProducts();
    }
}
