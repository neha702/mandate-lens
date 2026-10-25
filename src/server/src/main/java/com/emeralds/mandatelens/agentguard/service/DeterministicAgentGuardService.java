package com.emeralds.mandatelens.agentguard.service;

import com.emeralds.mandatelens.agentguard.client.AgentGuardClient;
import com.emeralds.mandatelens.agentguard.model.GuardEvaluationContext;
import com.emeralds.mandatelens.agentguard.model.*;
import com.emeralds.mandatelens.mandate.model.Mandate;
import com.emeralds.mandatelens.mandate.service.MandateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Local deterministic implementation of the AgentGuard contract; activate only for development. */
@Component
@Profile("agentguard-deterministic")
public class DeterministicAgentGuardService implements AgentGuardClient {
    private final MandateService mandates;
    private final List<RuleEvaluator> evaluators;
    private final DecisionAggregator aggregator;
    private final ObjectMapper mapper;
    private final String policyVersion;
    private final AnomalyMode anomalyMode;

    public DeterministicAgentGuardService(MandateService mandates, List<RuleEvaluator> evaluators,
                                         DecisionAggregator aggregator, ObjectMapper mapper,
                                         @Value("${agentguard.policy-version:guard-policy-v1}") String policyVersion,
                                         @Value("${agentguard.anomaly.mode:OFF}") String anomalyMode) {
        this.mandates = mandates;
        this.evaluators = List.copyOf(evaluators);
        this.aggregator = aggregator;
        this.mapper = mapper;
        this.policyVersion = policyVersion;
        this.anomalyMode = AnomalyMode.parse(anomalyMode);
    }

    @Override
    public RegisterMandateResponse registerMandate(RegisterMandateRequest request) {
        if (request == null || request.mandateId() == null || request.journeyId() == null
                || request.authorization() == null
                || !"SIMULATED".equals(request.authorization().verificationStatus())) {
            throw new IllegalArgumentException("The deterministic demo engine only accepts simulated mandates.");
        }
        Mandate mandate = mandates.findByJourneyId(request.journeyId())
                .filter(found -> request.mandateId().equals(found.mandateId()))
                .orElseThrow(() -> new IllegalArgumentException("Mandate is not stored for this journey."));
        if (mandate.source() != Mandate.Source.SIMULATED_AP2) {
            throw new IllegalArgumentException("The local engine only registers simulated AP2 mandates.");
        }
        var authorization = request.authorization();
        var constraints = request.constraints();
        if (authorization.authorizationReference() == null
                || !mandate.authorizationReference().equals(authorization.authorizationReference())
                || mandate.authorizationEvidence() == null
                || !mandate.authorizationEvidence().protocolVersion().equals(authorization.protocolVersion())
                || constraints == null || constraints.maxAmount() == null
                || mandate.maxAmount().compareTo(constraints.maxAmount()) != 0
                || !mandate.currency().equalsIgnoreCase(constraints.currency())
                || !mandate.allowedCategories().equals(constraints.allowedCategories())
                || !mandate.allowedProductIds().equals(constraints.allowedProductIds())
                || !java.util.Objects.equals(mandate.shippingCountry(), constraints.shippingCountry())) {
            throw new IllegalArgumentException("AgentGuard registration fields do not match the stored authorization.");
        }
        return new RegisterMandateResponse(request.mandateId(), "REGISTERED");
    }

    @Override
    public GuardDecisionResponse evaluateCart(EvaluateCartRequest request) {
        if (request == null || request.proposedCart() == null) {
            throw new IllegalArgumentException("proposedCart is required.");
        }
        return evaluate(request.journeyId(), request.mandateId(), request.action(),
                request.currentCart(), request.proposedCart());
    }

    @Override
    public GuardDecisionResponse evaluateCheckout(EvaluateCheckoutRequest request) {
        if (request == null || request.cart() == null) throw new IllegalArgumentException("cart is required.");
        return evaluate(request.journeyId(), request.mandateId(), "CHECKOUT", request.cart(), request.cart());
    }

    private GuardDecisionResponse evaluate(String journeyId, String mandateId, String action,
                                           GuardCart current, GuardCart proposed) {
        Instant at = Instant.now();
        Mandate mandate = journeyId == null ? null : mandates.findByJourneyId(journeyId)
                .filter(found -> mandateId != null && mandateId.equals(found.mandateId())).orElse(null);
        String operation = "CHECKOUT".equals(action) ? "CHECKOUT" : action;
        GuardEvaluationContext context = new GuardEvaluationContext(operation, journeyId, mandateId,
                mandate, current, proposed, at, policyVersion);

        List<RuleFinding> findings = new ArrayList<>();
        evaluators.stream().filter(evaluator -> "DETERMINISTIC".equals(evaluator.evaluatorType()))
                .forEach(evaluator -> findings.addAll(evaluator.evaluate(context)));
        boolean hardBlock = findings.stream().anyMatch(f -> f.effect() == RuleFinding.Effect.BLOCK);
        if (!hardBlock && anomalyMode != AnomalyMode.OFF) {
            evaluators.stream().filter(evaluator -> !"DETERMINISTIC".equals(evaluator.evaluatorType()))
                    .forEach(evaluator -> {
                        List<RuleFinding> extra = evaluator.evaluate(context);
                        findings.addAll(anomalyMode == AnomalyMode.SHADOW ? shadow(extra) : extra);
                    });
        }

        GuardDecision decision = aggregator.aggregate(findings);
        List<GuardDecisionResponse.Reason> reasons = findings.stream()
                .filter(f -> f.effect() != RuleFinding.Effect.NONE)
                .map(f -> new GuardDecisionResponse.Reason(f.reasonCode(), f.message())).toList();
        List<GuardDecisionResponse.RuleResult> results = findings.stream()
                .map(f -> new GuardDecisionResponse.RuleResult(f.ruleId(), f.result().name(), f.message())).toList();
        String fingerprint = fingerprint(journeyId, mandateId, action, current, proposed);
        return new GuardDecisionResponse("D-" + UUID.randomUUID(), decision, reasons, results,
                policyVersion, at, fingerprint, findings);
    }

    private String fingerprint(String journeyId, String mandateId, String action,
                               GuardCart current, GuardCart proposed) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(Map.of("journeyId", journeyId == null ? "" : journeyId,
                    "mandateId", mandateId == null ? "" : mandateId, "action", action == null ? "" : action,
                    "currentCart", current == null ? Map.of() : current,
                    "proposedCart", proposed == null ? Map.of() : proposed));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint evaluated cart snapshot.", exception);
        }
    }

    private List<RuleFinding> shadow(List<RuleFinding> findings) {
        return findings.stream().map(finding -> {
            Map<String, Object> metadata = new LinkedHashMap<>(finding.modelMetadata());
            metadata.put("mode", "SHADOW");
            return new RuleFinding(finding.ruleId(), finding.ruleGroup(), finding.evaluatorType(),
                    finding.result(), RuleFinding.Effect.NONE, finding.reasonCode(), finding.message(),
                    finding.evidence(), finding.confidence(), metadata);
        }).toList();
    }

    private enum AnomalyMode {
        OFF, SHADOW, ENFORCE;
        static AnomalyMode parse(String mode) {
            try { return valueOf(mode == null ? "OFF" : mode.trim().toUpperCase()); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("agentguard.anomaly.mode must be OFF, SHADOW, or ENFORCE.");
            }
        }
    }
}
