package com.emeralds.mandatelens.agentguard.service;

import com.emeralds.mandatelens.agentguard.model.GuardEvaluationContext;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;

import java.util.List;

public interface RuleEvaluator {
    String evaluatorType();
    List<RuleFinding> evaluate(GuardEvaluationContext context);
}
