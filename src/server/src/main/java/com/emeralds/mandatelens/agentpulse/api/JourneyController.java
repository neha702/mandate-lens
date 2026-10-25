package com.emeralds.mandatelens.agentpulse.api;

import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
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

    public JourneyController(EventStore eventStore) {
        this.eventStore = eventStore;
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
