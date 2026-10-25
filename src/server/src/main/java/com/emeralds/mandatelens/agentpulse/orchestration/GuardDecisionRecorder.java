package com.emeralds.mandatelens.agentpulse.orchestration;

import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;
import com.emeralds.mandatelens.agentguard.repository.GuardDecisionRepository;
import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import com.emeralds.mandatelens.agentpulse.operation.PendingOperationRepository;
import com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GuardDecisionRecorder {
    private final PendingOperationRepository operations;
    private final GuardDecisionRepository decisions;
    private final EventStore events;

    public GuardDecisionRecorder(PendingOperationRepository operations, GuardDecisionRepository decisions,
                                 EventStore events) {
        this.operations = operations;
        this.decisions = decisions;
        this.events = events;
    }

    @Transactional
    public void record(String operationId, String journeyId, String mandateId, String action,
                       String decisionId, GuardDecision decision,
                       List<GuardDecisionResponse.Reason> reasons,
                       List<GuardDecisionResponse.RuleResult> ruleResults,
                       List<RuleFinding> findings,
                       String policyVersion, Instant evaluatedAt, String contextFingerprint,
                       MerchantOperationRequests.Check request, String authorizationHash) {
        decisions.save(decisionId, journeyId, mandateId, action, operationId, decision,
                reasons, Map.of("request", request, "ruleResults", ruleResults,
                        "findings", findings, "policyVersion", policyVersion == null ? "unknown" : policyVersion,
                        "evaluatedAt", evaluatedAt == null ? Instant.now() : evaluatedAt,
                        "contextFingerprint", contextFingerprint == null ? "" : contextFingerprint,
                        "authorizationHash", authorizationHash));
        operations.attachGuardDecision(operationId, decisionId, decision.name());
        events.save(new PurchaseEvent("evt-guard-" + operationId, journeyId, Instant.now(),
                "CART_ACTION_CHECKED", "AGENT_GUARD", request.proposedCart().total(),
                request.proposedCart().currency(), "operationId=" + operationId + ";decision="
                + decision + ";reasons=" + reasons + ";ruleResults=" + ruleResults
                + ";findingCount=" + findings.size()));
    }
}
