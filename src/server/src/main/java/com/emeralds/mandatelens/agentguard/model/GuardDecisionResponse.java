package com.emeralds.mandatelens.agentguard.model;

import java.util.List;

public record GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons,
                                    List<RuleResult> ruleResults) {
    public GuardDecisionResponse {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
    }
    public GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons) {
        this(decisionId, decision, reasons, List.of());
    }
    public record Reason(String code, String message) { }
    public record RuleResult(String ruleId, String result, String message) { }
}
