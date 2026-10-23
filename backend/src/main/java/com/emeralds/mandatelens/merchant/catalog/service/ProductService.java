package com.emeralds.mandatelens.merchant.catalog.service;

import com.emeralds.mandatelens.merchant.catalog.model.Product;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the PostgreSQL catalog; the bundled CSV is only a first-run seed. */
@Service
public class ProductService implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM merchant.products", Integer.class);
        if (count != null && count > 0) return;
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/products.csv").getInputStream(), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder().setHeader()
                    .setSkipHeaderRecord(true).get().parse(reader);
            for (CSVRecord row : records) {
                int quantity = Integer.parseInt(row.get("availableQuantity"));
                jdbc.update("""
                        INSERT INTO merchant.products
                        (product_id, name, description, category, price, currency, image_url, is_active, available_quantity)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (product_id) DO NOTHING
                        """, row.get("id"), row.get("name"), row.get("description"), row.get("category"),
                        new BigDecimal(row.get("price")), row.get("currency"), row.get("imageUrl"),
                        "IN_STOCK".equalsIgnoreCase(row.get("availability")), quantity);
            }
        }
    }

    public List<Product> getProducts() {
        return jdbc.query("""
                SELECT product_id, name, description, category, price, currency, image_url,
                       is_active, available_quantity
                FROM merchant.products ORDER BY product_id
                """, (rs, row) -> new Product(rs.getString("product_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("category"), rs.getBigDecimal("price"),
                rs.getString("currency").trim(), rs.getString("image_url"),
                rs.getBoolean("is_active") && rs.getInt("available_quantity") > 0 ? "IN_STOCK" : "OUT_OF_STOCK",
                rs.getInt("available_quantity")));
    }
}
