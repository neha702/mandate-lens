package com.emeralds.mandatelens.agentguard.model;

import java.util.List;

public record GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons) {
    public GuardDecisionResponse { reasons = reasons == null ? List.of() : List.copyOf(reasons); }
    public record Reason(String code, String message) { }
}
