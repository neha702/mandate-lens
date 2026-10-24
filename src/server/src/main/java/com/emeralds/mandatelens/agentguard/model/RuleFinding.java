package com.emeralds.mandatelens.agentguard.model;

import java.util.Map;

/** Stable result contract shared by deterministic and future anomaly evaluators. */
public record RuleFinding(String ruleId, String ruleGroup, String evaluatorType, Result result,
                          Effect effect, String reasonCode, String message,
                          Map<String, Object> evidence, Double confidence,
                          Map<String, Object> modelMetadata) {
    public RuleFinding {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        modelMetadata = modelMetadata == null ? Map.of() : Map.copyOf(modelMetadata);
    }

    public enum Result { PASS, FAIL, REVIEW, NOT_APPLICABLE, ERROR }
    public enum Effect { NONE, REVIEW, BLOCK }
}
