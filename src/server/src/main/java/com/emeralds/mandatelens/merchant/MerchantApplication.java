package com.emeralds.mandatelens.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Independent entry point for the merchant catalog, cart, and payment APIs. */
@SpringBootApplication(scanBasePackages = {
        "com.emeralds.mandatelens.merchant",
        "com.emeralds.mandatelens.shared.config"
})
public class MerchantApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(MerchantApplication.class)
                .profiles("merchant-server", "storesync-simulator")
                .run(args);
    }
}
