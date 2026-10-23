package com.emeralds.mandatelens.agentpulse.orchestration;

import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
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
    private final EventStore events;

    public GuardDecisionRecorder(PendingOperationRepository operations, EventStore events) {
        this.operations = operations;
        this.events = events;
    }

    @Transactional
    public void record(String operationId, String journeyId, String mandateId, String action,
                       String decisionId, GuardDecision decision,
                       List<GuardDecisionResponse.Reason> reasons,
                       List<GuardDecisionResponse.RuleResult> ruleResults,
                       MerchantOperationRequests.Check request) {
        operations.saveDecision(operationId, journeyId, mandateId, action, decisionId,
                decision.name(), reasons, Map.of("request", request, "ruleResults", ruleResults));
        events.save(new PurchaseEvent("evt-guard-" + operationId, journeyId, Instant.now(),
                "CART_ACTION_CHECKED", "AGENT_GUARD", request.proposedCart().total(),
                request.proposedCart().currency(), "operationId=" + operationId + ";decision="
                + decision + ";reasons=" + reasons + ";ruleResults=" + ruleResults));
    }
}
