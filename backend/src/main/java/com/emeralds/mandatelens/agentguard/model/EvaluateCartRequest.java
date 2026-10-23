package com.emeralds.mandatelens.agentguard.model;

public record EvaluateCartRequest(String journeyId, String mandateId, String action,
                                  GuardCart currentCart, GuardCart proposedCart) { }
