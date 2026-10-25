package com.emeralds.mandatelens.agentguard.model;

import com.emeralds.mandatelens.mandate.model.Mandate;

import java.time.Instant;

/** Immutable facts evaluated for one guard request. */
public record GuardEvaluationContext(String operation, String journeyId, String mandateId,
                                     Mandate mandate, GuardCart currentCart, GuardCart proposedCart,
                                     Instant evaluatedAt, String policyVersion) { }
