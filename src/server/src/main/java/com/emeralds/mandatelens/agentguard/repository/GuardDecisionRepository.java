package com.emeralds.mandatelens.agentguard.repository;

import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Persistence for AgentGuard decisions and their structured evidence. */
@Repository
public class GuardDecisionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public GuardDecisionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(String decisionId, String journeyId, String mandateId, String action,
                     String operationId, GuardDecision decision,
                     List<GuardDecisionResponse.Reason> reasons, Object actionContext) {
        jdbc.update("""
                INSERT INTO trust.guard_decisions
                    (decision_id, journey_id, mandate_id, action_type, decision, reason_details,
                     action_context, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """, decisionId, journeyId, mandateId, action, decision.name(),
                json(reasons), json(actionContext), operationId);
    }

    public Optional<DecisionRecord> find(String decisionId) {
        return jdbc.query("SELECT decision, reason_details::text, action_context::text "
                        + "FROM trust.guard_decisions WHERE decision_id = ?",
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    try {
                        List<GuardDecisionResponse.Reason> reasons = mapper.readValue(rs.getString(2),
                                mapper.getTypeFactory().constructCollectionType(List.class,
                                        GuardDecisionResponse.Reason.class));
                        JsonNode context = mapper.readTree(rs.getString(3));
                        List<GuardDecisionResponse.RuleResult> rules = readList(context.path("ruleResults"),
                                GuardDecisionResponse.RuleResult.class);
                        List<RuleFinding> findings = readList(context.path("findings"), RuleFinding.class);
                        return Optional.of(new DecisionRecord(GuardDecision.valueOf(rs.getString(1)), reasons,
                                rules, context.path("policyVersion").asText(null),
                                context.path("evaluatedAt").isMissingNode() || context.path("evaluatedAt").isNull()
                                        ? null : java.time.Instant.parse(context.path("evaluatedAt").asText()),
                                context.path("contextFingerprint").asText(null), findings));
                    } catch (Exception exception) {
                        throw new IllegalStateException("Could not read saved AgentGuard decision.", exception);
                    }
                }, decisionId);
    }

    private <T> List<T> readList(JsonNode node, Class<T> type) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        return mapper.convertValue(node, mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not encode AgentGuard decision evidence.", exception);
        }
    }

    public record DecisionRecord(GuardDecision decision, List<GuardDecisionResponse.Reason> reasons,
                                 List<GuardDecisionResponse.RuleResult> ruleResults, String policyVersion,
                                 java.time.Instant evaluatedAt, String contextFingerprint,
                                 List<RuleFinding> findings) { }
}
