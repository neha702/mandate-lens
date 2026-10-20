package com.emeralds.mandatelens.guard;

import com.emeralds.mandatelens.guard.model.GuardCheckRequest;
import com.emeralds.mandatelens.guard.model.GuardCheckResponse;

/**
 * Replaceable boundary between AgentPulse and AgentGuard.
 * The current implementation is a local demo; the other team can replace it with
 * their agreed Java module or HTTP integration without changing the controller contract.
 */
public interface AgentGuardClient {
    GuardCheckResponse checkPurchase(GuardCheckRequest request);
}
