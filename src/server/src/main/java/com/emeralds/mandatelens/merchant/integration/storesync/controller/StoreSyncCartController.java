package com.emeralds.mandatelens.merchant.integration.storesync.controller;

import com.emeralds.mandatelens.agentguard.client.HttpAgentGuardClient;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartResponse;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCheckoutRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.service.PayPalOrdersClient;
import com.emeralds.mandatelens.merchant.integration.storesync.service.StoreSyncCartService;
import com.emeralds.mandatelens.merchant.integration.storesync.service.StoreSyncGuardService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Local Store Sync simulator routes. This controller has no PayPal JWT verifier yet,
 * so activate only in a local development environment and never expose it publicly.
 */
@RestController
@Profile("storesync-simulator")
@RequestMapping("/merchant-cart")
public class StoreSyncCartController {
    private final StoreSyncCartService cartService;

    public StoreSyncCartController(StoreSyncCartService cartService) {
        this.cartService = cartService;
    }

    /** Creates the merchant cart and its PayPal Orders v2 payment context. */
    @PostMapping
    public StoreSyncCartResponse create(@Valid @RequestBody StoreSyncCartRequest request) {
        try {
            return cartService.create(request);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load merchant catalog.");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalNotConfiguredException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalGatewayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /** Replaces the complete cart and patches the linked PayPal order amount. */
    @PutMapping("/{cartId}")
    public StoreSyncCartResponse replace(@PathVariable String cartId,
                                         @Valid @RequestBody StoreSyncCartRequest request) {
        try {
            return cartService.replace(cartId, request);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load merchant catalog.");
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalNotConfiguredException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalGatewayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        } catch (HttpAgentGuardClient.AgentGuardUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (StoreSyncGuardService.GuardDecisionDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (StoreSyncGuardService.GuardIntegrationException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{cartId}")
    public StoreSyncCartResponse get(@PathVariable String cartId) {
        try {
            return cartService.get(cartId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    /** Checkout evaluates the cart with AgentGuard before any PayPal capture request. */
    @PostMapping("/{cartId}/checkout")
    public StoreSyncCartResponse checkout(@PathVariable String cartId,
                                          @Valid @RequestBody StoreSyncCheckoutRequest request) {
        try {
            return cartService.checkout(cartId, request);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load merchant catalog.");
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalNotConfiguredException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (PayPalOrdersClient.PayPalGatewayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        } catch (HttpAgentGuardClient.AgentGuardUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (StoreSyncGuardService.GuardDecisionDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (StoreSyncGuardService.GuardIntegrationException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
