package com.emeralds.mandatelens.agentguard.service;

import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionAggregator {
    public GuardDecision aggregate(List<RuleFinding> findings) {
        if (findings.stream().anyMatch(f -> f.effect() == RuleFinding.Effect.BLOCK)) return GuardDecision.BLOCK;
        if (findings.stream().anyMatch(f -> f.effect() == RuleFinding.Effect.REVIEW)) return GuardDecision.REVIEW;
        return GuardDecision.ALLOW;
    }
}
