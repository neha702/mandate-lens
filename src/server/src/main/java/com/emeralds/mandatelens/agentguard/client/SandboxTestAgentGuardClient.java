package com.emeralds.mandatelens.agentguard.client;

import com.emeralds.mandatelens.agentguard.model.EvaluateCartRequest;
import com.emeralds.mandatelens.agentguard.model.EvaluateCheckoutRequest;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateRequest;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Permissive local-only Guard stub for PayPal Sandbox protocol testing. */
@Component
@Profile("storesync-sandbox-test")
public class SandboxTestAgentGuardClient implements AgentGuardClient {
    @Override
    public RegisterMandateResponse registerMandate(RegisterMandateRequest request) {
        return new RegisterMandateResponse(request.mandateId(), "REGISTERED");
    }

    @Override
    public GuardDecisionResponse evaluateCart(EvaluateCartRequest request) {
        return allow();
    }

    @Override
    public GuardDecisionResponse evaluateCheckout(EvaluateCheckoutRequest request) {
        return allow();
    }

    private GuardDecisionResponse allow() {
        return new GuardDecisionResponse("sandbox-test-" + UUID.randomUUID(), GuardDecision.ALLOW,
                List.of(), List.of(), "sandbox-test-bypass", Instant.now(), "", List.of());
    }
}
