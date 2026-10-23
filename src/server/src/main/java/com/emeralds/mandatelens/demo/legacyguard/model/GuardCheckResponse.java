package com.emeralds.mandatelens.demo.legacyguard.model;

import java.util.List;

/** Draft answer format from AgentGuard back to AgentPulse. */
public record GuardCheckResponse(
        GuardDecision decision,
        String reasonCode,
        String message,
        List<String> evidenceEventIds,
        List<String> missingInformation
) {
    public GuardCheckResponse {
        evidenceEventIds = evidenceEventIds == null ? List.of() : List.copyOf(evidenceEventIds);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }
}
