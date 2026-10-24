package com.emeralds.mandatelens.agentguard.model;

import java.util.List;
import java.time.Instant;

public record GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons,
                                    List<RuleResult> ruleResults, String policyVersion,
                                    Instant evaluatedAt, String contextFingerprint,
                                    List<RuleFinding> findings) {
    public GuardDecisionResponse {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
    public GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons) {
        this(decisionId, decision, reasons, List.of(), null, null, null, List.of());
    }
    public GuardDecisionResponse(String decisionId, GuardDecision decision, List<Reason> reasons,
                                 List<RuleResult> ruleResults) {
        this(decisionId, decision, reasons, ruleResults, null, null, null, List.of());
    }
    public record Reason(String code, String message) { }
    public record RuleResult(String ruleId, String result, String message) { }
}
