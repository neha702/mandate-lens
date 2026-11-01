package com.emeralds.mandatelens.mandate.api;

import com.emeralds.mandatelens.agentguard.client.HttpAgentGuardClient;
import com.emeralds.mandatelens.mandate.service.MandateRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal Merchant-to-Mandate-Lens endpoint for creating a demo budget-constrained journey. */
@RestController
@RequestMapping("/api/v1/shopping-sessions")
public class ShoppingSessionController {
    private final MandateRegistrationService registrations;

    public ShoppingSessionController(MandateRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping
    public MandateRegistrationService.ShoppingSessionResponse start(
            @RequestBody MandateRegistrationService.ShoppingSessionRequest request) {
        try {
            return registrations.startShoppingSession(request);
        } catch (HttpAgentGuardClient.AgentGuardUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        }
    }
}
