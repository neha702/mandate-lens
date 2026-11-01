package com.emeralds.mandatelens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.emeralds.mandatelens.agentpulse",
        "com.emeralds.mandatelens.agentguard",
        "com.emeralds.mandatelens.mandate",
        "com.emeralds.mandatelens.shared.config"
})
@EnableScheduling
public class MandateLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(MandateLensApplication.class, args);
    }
}
