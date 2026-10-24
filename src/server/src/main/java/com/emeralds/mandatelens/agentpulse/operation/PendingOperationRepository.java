package com.emeralds.mandatelens.agentpulse.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class PendingOperationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PendingOperationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<Operation> findByIdempotencyKey(String merchantId, String key) {
        return jdbc.query("SELECT operation_id, journey_id, cart_id, action_type, status, guard_decision_id, merchant_id, request_hash " +
                        "FROM trust.pending_operations WHERE merchant_id = ? AND idempotency_key = ?",
                rs -> rs.next() ? Optional.of(new Operation(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8))) : Optional.empty(), merchantId, key);
    }

    public void create(String operationId, String journeyId, String merchantId, String cartId,
                       String action, String idempotencyKey, Object context, Instant dueAt) {
        try {
            jdbc.update("""
                    INSERT INTO trust.pending_operations
                    (operation_id, journey_id, merchant_id, cart_id, action_type, status,
                     idempotency_key, request_hash, request_context, result_due_at)
                    VALUES (?, ?, ?, ?, ?, 'PENDING_CHECK', ?, ?, ?::jsonb, ?)
                    """, operationId, journeyId, merchantId, cartId, action, idempotencyKey,
                    fingerprint(context), json(context), Timestamp.from(dueAt));
        } catch (DuplicateKeyException duplicate) {
            throw new IdempotencyConflictException();
        }
    }

    public void createResultOnly(String operationId, String journeyId, String merchantId, String cartId,
                                 String eventId, Object context, Instant dueAt) {
        jdbc.update("""
                INSERT INTO trust.pending_operations
                (operation_id, journey_id, merchant_id, cart_id, action_type, status,
                 idempotency_key, request_hash, request_context, result_due_at)
                VALUES (?, ?, ?, ?, 'ITEM_REMOVAL', 'AWAITING_MERCHANT_RESULT', ?, ?, ?::jsonb, ?)
                """, operationId, journeyId, merchantId, cartId, "callback:" + eventId,
                fingerprint(context), json(context), Timestamp.from(dueAt));
    }

    @Transactional
    public void saveDecision(String operationId, String journeyId, String mandateId, String action,
                             String decisionId, String decision, Object reasons, Object context) {
        jdbc.update("""
                INSERT INTO trust.guard_decisions
                (decision_id, journey_id, mandate_id, action_type, decision, reason_details, action_context,
                 correlation_id)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """, decisionId, journeyId, mandateId, action, decision, json(reasons), json(context), operationId);
        jdbc.update("""
                UPDATE trust.pending_operations SET guard_decision_id = ?, status = ?,
                    resolved_at = CASE WHEN ? = 'BLOCK' THEN NOW() ELSE NULL END
                WHERE operation_id = ?
                """, decisionId, "BLOCK".equals(decision) ? "NOT_APPLIED" : "AWAITING_MERCHANT_RESULT",
                decision, operationId);
    }

    public Optional<DecisionRecord> findDecision(String decisionId) {
        return jdbc.query("SELECT decision, reason_details::text, action_context::text " +
                        "FROM trust.guard_decisions WHERE decision_id = ?",
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    try {
                        java.util.List<GuardDecisionResponse.Reason> reasons = mapper.readValue(rs.getString(2), mapper.getTypeFactory()
                                .constructCollectionType(java.util.List.class, GuardDecisionResponse.Reason.class));
                        var context = mapper.readTree(rs.getString(3));
                        var rulesNode = context.path("ruleResults");
                        java.util.List<GuardDecisionResponse.RuleResult> rules = mapper.convertValue(rulesNode, mapper.getTypeFactory()
                                .constructCollectionType(java.util.List.class, GuardDecisionResponse.RuleResult.class));
                        return Optional.of(new DecisionRecord(GuardDecision.valueOf(rs.getString(1)), reasons, rules));
                    } catch (Exception exception) {
                        throw new IllegalStateException("Could not read saved Guard decision.", exception);
                    }
                }, decisionId);
    }

    public Operation find(String operationId) {
        return jdbc.query("SELECT operation_id, journey_id, cart_id, action_type, status, guard_decision_id, merchant_id, request_hash " +
                        "FROM trust.pending_operations WHERE operation_id = ?",
                rs -> rs.next() ? new Operation(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)) : null, operationId);
    }

    public void markCheckFailed(String operationId) {
        jdbc.update("UPDATE trust.pending_operations SET status = 'CHECK_FAILED', resolved_at = NOW() " +
                "WHERE operation_id = ? AND status = 'PENDING_CHECK'", operationId);
    }

    @Transactional
    public boolean saveCallback(String eventId, String operationId, Object payload,
                                String status, String eventType, boolean cartEmpty, Instant occurredAt) {
        try {
            jdbc.update("INSERT INTO trust.merchant_callback_events (event_id, operation_id, payload) " +
                            "VALUES (?, ?, ?::jsonb)", eventId, operationId, json(payload));
        } catch (DuplicateKeyException duplicate) {
            return jdbc.query("SELECT operation_id, payload FROM trust.merchant_callback_events WHERE event_id = ?",
                    rs -> {
                        if (!rs.next()) throw new DuplicateCallbackConflictException();
                        try {
                            boolean same = operationId.equals(rs.getString(1))
                                    && mapper.readTree(rs.getString(2)).equals(mapper.valueToTree(payload));
                            if (!same) throw new DuplicateCallbackConflictException();
                            return false;
                        } catch (JsonProcessingException exception) {
                            throw new IllegalStateException("Could not validate duplicate callback.", exception);
                        }
                    }, eventId);
        }
        int changed = jdbc.update("""
                UPDATE trust.pending_operations SET status = ?, result_event_id = ?, resolved_at = ?
                WHERE operation_id = ? AND status = 'AWAITING_MERCHANT_RESULT'
                """, status, eventId, Timestamp.from(occurredAt), operationId);
        if (changed == 0) throw new OperationStateException("Operation is not awaiting a merchant result.");
        if ("PAYMENT_SUCCEEDED".equals(eventType)) {
            jdbc.update("UPDATE trust.trust_journeys SET status = 'COMPLETED', ended_at = ?, updated_at = NOW() " +
                    "WHERE journey_id = ? AND status IN ('ACTIVE', 'EMPTY')", Timestamp.from(occurredAt), find(operationId).journeyId());
        } else if (eventType.startsWith("ITEM_REMOVAL_") && "SUCCEEDED".equals(status) && cartEmpty) {
            jdbc.update("UPDATE trust.trust_journeys SET status = 'EMPTY', updated_at = NOW() " +
                    "WHERE journey_id = ? AND status = 'ACTIVE'", find(operationId).journeyId());
        } else if ((eventType.startsWith("ITEM_REMOVAL_") || eventType.startsWith("CART_"))
                && "SUCCEEDED".equals(status) && !cartEmpty) {
            jdbc.update("UPDATE trust.trust_journeys SET status = 'ACTIVE', updated_at = NOW() " +
                    "WHERE journey_id = ? AND status = 'EMPTY'", find(operationId).journeyId());
        }
        return true;
    }

    public java.util.List<ExpiredOperation> expirePending(Instant now) {
        return jdbc.query("""
                UPDATE trust.pending_operations SET status = 'TIMED_OUT', resolved_at = ?
                WHERE status = 'AWAITING_MERCHANT_RESULT' AND result_due_at <= ?
                RETURNING operation_id, journey_id, cart_id
                """, (rs, rowNum) -> new ExpiredOperation(rs.getString(1), rs.getString(2), rs.getString(3)),
                Timestamp.from(now), Timestamp.from(now));
    }

    public java.util.List<String> expireInactiveJourneys(Instant inactiveBefore, Instant now) {
        return jdbc.query("""
                UPDATE trust.trust_journeys SET status = 'EXPIRED', ended_at = ?, updated_at = NOW()
                WHERE status IN ('ACTIVE', 'EMPTY')
                  AND COALESCE(last_event_at, started_at) <= ?
                RETURNING journey_id
                """, (rs, rowNum) -> rs.getString(1), Timestamp.from(now), Timestamp.from(inactiveBefore));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Could not encode operation data.", exception); }
    }

    public String fingerprint(Object value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record Operation(String operationId, String journeyId, String cartId,
                            String action, String status, String decisionId, String merchantId,
                            String requestHash) { }
    public record DecisionRecord(GuardDecision decision, java.util.List<GuardDecisionResponse.Reason> reasons,
                                 java.util.List<GuardDecisionResponse.RuleResult> ruleResults) { }
    public record ExpiredOperation(String operationId, String journeyId, String cartId) { }
    public static class IdempotencyConflictException extends RuntimeException { }
    public static class OperationStateException extends RuntimeException {
        public OperationStateException(String message) { super(message); }
    }
    public static class DuplicateCallbackConflictException extends RuntimeException { }
}
