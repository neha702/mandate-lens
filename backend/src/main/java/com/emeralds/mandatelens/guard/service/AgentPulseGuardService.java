package com.emeralds.mandatelens.guard.service;

import com.emeralds.mandatelens.guard.AgentGuardClient;
import com.emeralds.mandatelens.guard.model.GuardCheckRequest;
import com.emeralds.mandatelens.guard.model.GuardCheckResponse;
import com.emeralds.mandatelens.model.PurchaseEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        String currency = latestEvent.currency();
        String limitSource = events.stream()
                .filter(event -> event.details() != null && event.details().contains("budget"))
                .map(PurchaseEvent::source)
                .findFirst()
                .orElse("simulated_demo");

        GuardCheckRequest request = new GuardCheckRequest(
                journeyId,
                new GuardCheckRequest.Money(shopperLimit.amount(),
                        shopperLimit.currency() == null ? currency : shopperLimit.currency(),
                        shopperLimit.source() == null ? limitSource : shopperLimit.source()),
                new GuardCheckRequest.Money(latestEvent.amount(), currency, latestEvent.source()),
                events.stream().map(event -> new GuardCheckRequest.GuardEvidenceEvent(
                        event.id(), event.type(), event.amount(), event.currency(), event.source())).toList()
        );
        return agentGuardClient.checkPurchase(request);
    }
}
