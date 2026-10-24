package com.emeralds.mandatelens.demo.legacyguard;

import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import com.emeralds.mandatelens.demo.legacyguard.AgentGuardClient;
import com.emeralds.mandatelens.demo.legacyguard.model.GuardCheckRequest;
import com.emeralds.mandatelens.demo.legacyguard.model.GuardCheckResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** Builds the draft AgentGuard request from one AgentPulse journey. */
@Service
public class AgentPulseGuardService {
    private final AgentGuardClient agentGuardClient;

    public AgentPulseGuardService(AgentGuardClient agentGuardClient) {
        this.agentGuardClient = agentGuardClient;
    }

    public GuardCheckResponse checkJourney(String journeyId, List<PurchaseEvent> events,
                                           GuardCheckRequest.Money shopperLimit) {
        PurchaseEvent latestEvent = events.get(events.size() - 1);
        GuardCheckRequest request = new GuardCheckRequest(
                journeyId,
                shopperLimit,
                new GuardCheckRequest.Money(latestEvent.amount(), latestEvent.currency(), latestEvent.source()),
                events.stream().map(event -> new GuardCheckRequest.GuardEvidenceEvent(
                        event.id(), event.type(), event.amount(), event.currency(), event.source())).toList()
        );
        return agentGuardClient.checkPurchase(request);
    }
}
