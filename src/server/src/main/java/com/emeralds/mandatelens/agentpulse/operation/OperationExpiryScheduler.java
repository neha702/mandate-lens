package com.emeralds.mandatelens.agentpulse.operation;

import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OperationExpiryScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OperationExpiryScheduler.class);

    private final PendingOperationRepository operations;
    private final EventStore events;

    public OperationExpiryScheduler(PendingOperationRepository operations, EventStore events) {
        this.operations = operations;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${mandatelens.lifecycle.sweep-interval-ms:30000}")
    @Transactional
    public void expire() {
        Instant now = Instant.now();
        var expiredOperations = operations.expirePending(now);
        for (var operation : expiredOperations) {
            events.save(new PurchaseEvent("evt-timeout-" + operation.operationId(), operation.journeyId(), now,
                    "OPERATION_TIMED_OUT", "SYSTEM", null, null,
                    "operationId=" + operation.operationId() + ";merchant result callback was not received"));
        }
        var expiredJourneys = operations.expireJourneysWithExpiredMandates(now);
        for (String journeyId : expiredJourneys) {
            events.save(new PurchaseEvent("evt-expired-" + journeyId, journeyId, now,
                    "JOURNEY_EXPIRED", "SYSTEM", null, null, "The linked mandate expired; the Merchant cart remains stored."));
        }
        if (!expiredOperations.isEmpty() || !expiredJourneys.isEmpty()) {
            logger.warn("Lifecycle expiry sweep completed: expiredOperations={} expiredJourneys={}",
                    expiredOperations.size(), expiredJourneys.size());
        }
    }
}
