package com.emeralds.mandatelens.agentpulse.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Health check for the independently running Mandate Lens API. */
@RestController
public class MandateLensHealthController {
    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "Mandate Lens API is running");
    }

    public record HealthResponse(String status, String message) { }
}
