package com.emeralds.mandatelens.agentguard.model;

public record EvaluateCheckoutRequest(String journeyId, String mandateId, GuardCart cart) { }
