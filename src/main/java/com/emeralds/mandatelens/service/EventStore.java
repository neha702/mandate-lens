package com.emeralds.mandatelens.service;

import com.emeralds.mandatelens.model.PurchaseEvent;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Volatile MVP store; data resets when the application restarts. */
@Repository
public class EventStore {
    private final ConcurrentMap<String, PurchaseEvent> events = new ConcurrentHashMap<>();

    public PurchaseEvent save(PurchaseEvent event) {
        if (events.putIfAbsent(event.id(), event) != null) {
            throw new DuplicateEventException(event.id());
        }
        return event;
    }

    public List<PurchaseEvent> findByJourneyId(String journeyId) {
        return events.values().stream()
                .filter(event -> event.journeyId().equals(journeyId))
                .sorted(Comparator.comparing(PurchaseEvent::occurredAt).thenComparing(PurchaseEvent::id))
                .toList();
    }

    public static class DuplicateEventException extends RuntimeException {
        public DuplicateEventException(String id) {
            super("Event id already exists: " + id);
        }
    }
}
