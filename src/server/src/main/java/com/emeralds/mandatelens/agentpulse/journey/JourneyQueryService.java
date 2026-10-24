package com.emeralds.mandatelens.agentpulse.journey;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Assembles the saved journey facts without calling AgentGuard or PayPal. */
@Service
public class JourneyQueryService {
    private final JourneyReadRepository repository;

    public JourneyQueryService(JourneyReadRepository repository) { this.repository = repository; }

    public JourneyDetail getJourney(String journeyId, String merchantId) {
        var journey = repository.findJourney(journeyId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Journey not found for this merchant."));
        List<JourneyReadRepository.OperationFact> operations = repository.findOperations(journeyId);
        List<JourneyReadRepository.DecisionFact> decisions = repository.findDecisions(journeyId);
        List<JourneyReadRepository.EventFact> events = repository.findEvents(journeyId);
        var latestEvent = events.isEmpty() ? null : events.get(events.size() - 1);
        String cartId = journey.cartId();
        if (cartId == null && !operations.isEmpty()) cartId = operations.get(operations.size() - 1).cartId();
        return new JourneyDetail(journey.journeyId(), journey.merchantId(), cartId, journey.status(),
                latestEvent == null ? null : latestEvent.type(), latestEvent == null ? null : latestEvent.outcome(),
                journey.startedAt(), journey.lastEventAt(), journey.endedAt(),
                operations, decisions, events);
    }

    public record JourneyDetail(String journeyId, String merchantId, String cartId, String status,
                                String latestEventType, String latestOutcome, Instant startedAt,
                                Instant lastEventAt, Instant endedAt,
                                List<JourneyReadRepository.OperationFact> operations,
                                List<JourneyReadRepository.DecisionFact> decisions,
                                List<JourneyReadRepository.EventFact> timeline) { }
}
