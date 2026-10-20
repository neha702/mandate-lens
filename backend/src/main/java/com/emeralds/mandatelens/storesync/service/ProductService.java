package com.emeralds.mandatelens.storesync.service;

import com.emeralds.mandatelens.storesync.model.Product;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    public List<Product> getProducts() throws IOException {

        List<Product> products = new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/products.csv").getInputStream(),
                StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records =
                    CSVFormat.DEFAULT.builder()
                            .setHeader()
                            .setSkipHeaderRecord(true)
                            .get()
                            .parse(reader);

            for (CSVRecord record : records) {

                Product product = new Product(
                        record.get("id"),
                        record.get("name"),
                        record.get("description"),
                        record.get("category"),
                        new BigDecimal(record.get("price")),
                        record.get("currency"),
                        record.get("imageUrl"),
                        record.get("availability")
                );

                products.add(product);
            }
        }

        return products;
    }
}
