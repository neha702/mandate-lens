package com.emeralds.mandatelens.merchant.api;

import com.emeralds.mandatelens.merchant.integration.storesync.service.MandateLensShoppingSessionClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/** Public-to-demo MCP entry point; Merchant remains the only API the MCP client calls. */
@RestController
@RequestMapping("/api/merchant-sessions")
public class MerchantShoppingSessionController {
    private final MandateLensShoppingSessionClient sessions;

    public MerchantShoppingSessionController(MandateLensShoppingSessionClient sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public MandateLensShoppingSessionClient.ShoppingSession start(@Valid @RequestBody StartRequest request) {
        try {
            return sessions.start(request.sessionId(), request.maxAmount(), request.currency());
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().is4xxClientError()
                    ? HttpStatus.valueOf(exception.getStatusCode().value()) : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ResponseStatusException(status, "Mandate Lens could not start this shopping session.");
        }
    }

    public record StartRequest(@NotBlank String sessionId,
                               @NotNull @DecimalMin(value = "0.01") BigDecimal maxAmount,
                               @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) { }
}
