package com.emeralds.mandatelens.mandate.api;

import com.emeralds.mandatelens.agentguard.client.HttpAgentGuardClient;
import com.emeralds.mandatelens.mandate.service.MandateRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Hackathon intake for the simulated AP2 authorization bundle. */
@RestController
@RequestMapping("/api/v1/authorizations")
public class AuthorizationController {
    private final MandateRegistrationService registrations;

    public AuthorizationController(MandateRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping
    public MandateRegistrationService.RegistrationResponse register(
            @RequestBody MandateRegistrationService.RegistrationRequest request) {
        try {
            return registrations.register(request);
        } catch (HttpAgentGuardClient.AgentGuardUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        }
    }
}
