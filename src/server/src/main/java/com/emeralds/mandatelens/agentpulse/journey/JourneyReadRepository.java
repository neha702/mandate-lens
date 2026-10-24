package com.emeralds.mandatelens.agentpulse.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-only queries used to assemble a merchant's recorded journey history. */
@Repository
public class JourneyReadRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JourneyReadRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<JourneySummary> findJourney(String journeyId, String merchantId) {
        return jdbc.query("""
                        SELECT journey_id, merchant_id, cart_id, status, started_at, last_event_at, ended_at
                        FROM trust.trust_journeys
                        WHERE journey_id = ? AND merchant_id = ?
                        """, rs -> rs.next() ? Optional.of(new JourneySummary(
                        rs.getString("journey_id"), rs.getString("merchant_id"), rs.getString("cart_id"),
                        rs.getString("status"), instant(rs, "started_at"), instant(rs, "last_event_at"),
                        instant(rs, "ended_at"))) : Optional.empty(), journeyId, merchantId);
    }

    public List<OperationFact> findOperations(String journeyId) {
        return jdbc.query("""
                SELECT operation_id, cart_id, action_type, status, guard_decision_id,
                       result_event_id, created_at, resolved_at
                FROM trust.pending_operations
                WHERE journey_id = ?
                ORDER BY created_at, operation_id
                """, (rs, rowNum) -> new OperationFact(rs.getString("operation_id"), rs.getString("cart_id"),
                rs.getString("action_type"), rs.getString("status"), rs.getString("guard_decision_id"),
                rs.getString("result_event_id"), instant(rs, "created_at"), instant(rs, "resolved_at")), journeyId);
    }

    public List<DecisionFact> findDecisions(String journeyId) {
        return jdbc.query("""
                SELECT decision_id, correlation_id, action_type, decision, reason_details::text AS reasons,
                       action_context::text AS context, evaluated_at
                FROM trust.guard_decisions
                WHERE journey_id = ?
                ORDER BY evaluated_at, decision_id
                """, (rs, rowNum) -> new DecisionFact(rs.getString("decision_id"),
                rs.getString("correlation_id"), rs.getString("action_type"), rs.getString("decision"),
                json(rs.getString("reasons")), ruleResults(rs.getString("context")),
                instant(rs, "evaluated_at")), journeyId);
    }

    public List<EventFact> findEvents(String journeyId) {
        return jdbc.query("""
                SELECT event_id, event_type, source, outcome, correlation_id, details::text AS details,
                       occurred_at
                FROM trust.journey_events
                WHERE journey_id = ?
                ORDER BY occurred_at, event_id
                """, (rs, rowNum) -> new EventFact(rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("source"), rs.getString("outcome"), rs.getString("correlation_id"),
                json(rs.getString("details")), instant(rs, "occurred_at")), journeyId);
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Could not read journey JSON data.", exception); }
    }

    private JsonNode ruleResults(String context) {
        JsonNode rules = json(context).path("ruleResults");
        return rules.isMissingNode() || rules.isNull() ? mapper.createArrayNode() : rules;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record JourneySummary(String journeyId, String merchantId, String cartId, String status,
                                 Instant startedAt, Instant lastEventAt, Instant endedAt) { }
    public record OperationFact(String operationId, String cartId, String action, String status,
                                String decisionId, String resultEventId, Instant createdAt, Instant resolvedAt) { }
    public record DecisionFact(String decisionId, String operationId, String action, String decision,
                               JsonNode reasons, JsonNode ruleResults, Instant evaluatedAt) { }
    public record EventFact(String eventId, String type, String source, String outcome, String correlationId,
                            JsonNode details, Instant occurredAt) { }
}
