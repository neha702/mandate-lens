package com.emeralds.mandatelens.agentpulse.api;

import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import com.emeralds.mandatelens.demo.legacyguard.AgentPulseGuardService;
import com.emeralds.mandatelens.demo.legacyguard.model.GuardCheckRequest;
import com.emeralds.mandatelens.demo.legacyguard.model.GuardCheckResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class JourneyController {
    private final EventStore eventStore;
    private final AgentPulseGuardService guardService;

    public JourneyController(EventStore eventStore, AgentPulseGuardService guardService) {
        this.eventStore = eventStore;
        this.guardService = guardService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "AgentPulse API is running");
    }

    @GetMapping("/journeys/{journeyId}")
    public JourneyResponse getJourney(@PathVariable String journeyId) {
        List<PurchaseEvent> events = eventStore.findByJourneyId(journeyId);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found: " + journeyId);
        }
        return new JourneyResponse(journeyId, events);
    }

    /** Runs the draft AgentGuard contract against the journey using the supplied shopper limit. */
    @PostMapping("/journeys/{journeyId}/guard-check")
    public GuardCheckResponse checkJourney(@PathVariable String journeyId,
                                           @Valid @RequestBody AgentGuardCheckApiRequest request) {
        List<PurchaseEvent> events = eventStore.findByJourneyId(journeyId);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found: " + journeyId);
        }
        if (!journeyId.equals(request.journeyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The path journeyId must match the request journeyId.");
        }
        if (request.shopperLimit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopperLimit is required.");
        }
        return guardService.checkJourney(journeyId, events,
                new GuardCheckRequest.Money(
                        request.shopperLimit().amount(), request.shopperLimit().currency(), request.shopperLimit().source()));
    }

    @PostMapping("/journeys/{journeyId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseEvent addEvent(@PathVariable String journeyId, @Valid @RequestBody CreateEventRequest request) {
        try {
            return eventStore.save(new PurchaseEvent(request.id(), journeyId, request.occurredAt(),
                    request.type(), request.source(), request.amount(), request.currency(), request.details()));
        } catch (EventStore.DuplicateEventException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    public record HealthResponse(String status, String message) { }
    public record JourneyResponse(String journeyId, List<PurchaseEvent> events) { }

    public record AgentGuardCheckApiRequest(
            @NotBlank String journeyId,
            @NotNull @Valid MoneyRequest shopperLimit
    ) { }

    public record MoneyRequest(@NotNull BigDecimal amount, @NotBlank String currency, @NotBlank String source) { }

    public record CreateEventRequest(
            @NotBlank String id,
            @NotNull Instant occurredAt,
            @NotBlank String type,
            @NotBlank String source,
            BigDecimal amount,
            String currency,
            String details
    ) { }
}
