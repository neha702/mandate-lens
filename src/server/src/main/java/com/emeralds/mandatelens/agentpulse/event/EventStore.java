package com.emeralds.mandatelens.agentpulse.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PostgreSQL-backed AgentPulse journey event store. */
@Repository
public class EventStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EventStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PurchaseEvent save(PurchaseEvent event) {
        jdbc.update("""
                INSERT INTO trust.trust_journeys (journey_id, merchant_id)
                VALUES (?, 'mandatelens-demo') ON CONFLICT (journey_id) DO NOTHING
                """, event.journeyId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", event.details());
        details.put("sourceLabel", event.source());
        if (event.amount() != null) details.put("amount", event.amount());
        if (event.currency() != null) details.put("currency", event.currency());
        details.putAll(event.structuredDetails());
        String json = writeJson(details);
        try {
            jdbc.update("""
                    INSERT INTO trust.journey_events
                    (event_id, journey_id, event_type, source, outcome, details, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, event.id(), event.journeyId(), event.type(), normalizeSource(event.source()),
                    normalizeOutcome(event.type()), json, Timestamp.from(event.occurredAt()));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEventException(event.id());
        }
        jdbc.update("UPDATE trust.trust_journeys SET last_event_at = ?, updated_at = NOW() WHERE journey_id = ?",
                Timestamp.from(event.occurredAt()), event.journeyId());
        return event;
    }

    public List<PurchaseEvent> findByJourneyId(String journeyId) {
        return jdbc.query("""
                        SELECT event_id, journey_id, occurred_at, event_type, source, details::text AS details
                        FROM trust.journey_events WHERE journey_id = ?
                        ORDER BY occurred_at, event_id
                        """, (rs, rowNum) -> {
                    Map<String, Object> details = readJson(rs.getString("details"));
                    BigDecimal amount = decimal(details.get("amount"));
                    String currency = string(details.get("currency"));
                    String message = string(details.get("message"));
                    Map<String, Object> structured = new LinkedHashMap<>(details);
                    structured.keySet().removeAll(List.of("message", "sourceLabel", "amount", "currency"));
                    return new PurchaseEvent(rs.getString("event_id"), rs.getString("journey_id"),
                            rs.getTimestamp("occurred_at").toInstant(), rs.getString("event_type"),
                            string(details.getOrDefault("sourceLabel", rs.getString("source"))), amount, currency,
                            message, structured);
                }, journeyId).stream().sorted(Comparator.comparing(PurchaseEvent::occurredAt)
                .thenComparing(PurchaseEvent::id)).toList();
    }

    private String normalizeSource(String source) {
        if (source == null) return "SYSTEM";
        String value = source.toUpperCase();
        if (value.contains("PAYPAL")) return "PAYPAL_WEBHOOK";
        if (value.contains("GUARD")) return "AGENT_GUARD";
        if (value.contains("MERCHANT")) return "MERCHANT";
        return "SYSTEM";
    }

    private String normalizeOutcome(String type) {
        String value = type == null ? "" : type.toUpperCase();
        if (value.contains("BLOCK")) return "BLOCKED";
        if (value.contains("FAIL")) return "FAILED";
        if (value.contains("ATTEMPT")) return "ATTEMPTED";
        if (value.contains("PENDING")) return "PENDING";
        if (value.contains("CREATED") || value.contains("CAPTURED") || value.contains("COMPLETED")) return "SUCCESS";
        return "INFO";
    }

    private String writeJson(Map<String, Object> details) {
        try { return objectMapper.writeValueAsString(details); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Could not encode event details", exception); }
    }

    private Map<String, Object> readJson(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read event details", exception); }
    }

    private BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    private String string(Object value) { return value == null ? null : value.toString(); }

    public static class DuplicateEventException extends RuntimeException {
        public DuplicateEventException(String id) { super("Event id already exists: " + id); }
    }
}
