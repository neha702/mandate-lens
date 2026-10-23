package com.emeralds.mandatelens.agentguard.client;

import com.emeralds.mandatelens.agentguard.model.EvaluateCartRequest;
import com.emeralds.mandatelens.agentguard.model.EvaluateCheckoutRequest;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateRequest;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateResponse;

public interface AgentGuardClient {
    RegisterMandateResponse registerMandate(RegisterMandateRequest request);
    GuardDecisionResponse evaluateCart(EvaluateCartRequest request);
    GuardDecisionResponse evaluateCheckout(EvaluateCheckoutRequest request);
}
