package com.emeralds.mandatelens.mandate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.emeralds.mandatelens.agentguard.client.AgentGuardClient;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateRequest;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateResponse;
import com.emeralds.mandatelens.mandate.model.Ap2AuthorizationEnvelope;
import com.emeralds.mandatelens.mandate.model.Mandate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class MandateRegistrationService {
    private final MandateService mandates;
    private final AgentGuardClient guard;
    private final ObjectMapper mapper;

    public MandateRegistrationService(MandateService mandates, AgentGuardClient guard, ObjectMapper mapper) {
        this.mandates = mandates;
        this.guard = guard;
        this.mapper = mapper;
    }

    public RegistrationResponse register(RegistrationRequest request) {
        if (request == null || blank(request.mandateId()) || blank(request.journeyId())
                || blank(request.userId()) || blank(request.merchantId())) {
            throw new IllegalArgumentException("mandateId, journeyId, userId, and merchantId are required.");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future.");
        }
        Ap2AuthorizationEnvelope envelope = request.authorization();
        validate(envelope);
        var constraints = envelope.normalizedConstraints();
        Mandate mandate = new Mandate(request.mandateId(), request.journeyId(), request.userId(),
                request.merchantId(), envelope.authorizationId(), constraints.maxAmount(), constraints.currency(),
                constraints.allowedCategories(), constraints.allowedProductIds(), constraints.shippingCountry(),
                request.createdAt(), request.expiresAt(),
                Mandate.Source.SIMULATED_AP2, envelope, null);
        mandates.save(mandate);
        Mandate saved = mandates.findByJourneyId(mandate.journeyId())
                .orElseThrow(() -> new IllegalStateException("Saved mandate could not be loaded."));
        return ensureRegistered(saved);
    }

    /** Registers normalized constraints once; the original envelope remains in Mandate Lens. */
    public RegistrationResponse ensureRegistered(Mandate mandate) {
        String hash = fingerprint(mandate.authorizationEvidence());
        if (mandate.agentGuardRegisteredAt() != null) {
            return response(mandate, hash, "REGISTERED", mandate.agentGuardRegisteredAt());
        }
        RegisterMandateResponse response = guard.registerMandate(toGuardRequest(mandate, hash));
        if (response == null || !mandate.mandateId().equals(response.mandateId())
                || response.status() == null || !"ACTIVE".equalsIgnoreCase(response.status())
                && !"REGISTERED".equalsIgnoreCase(response.status())) {
            throw new IllegalStateException("AgentGuard did not confirm mandate registration.");
        }
        Instant registeredAt = Instant.now();
        mandates.markAgentGuardRegistered(mandate.mandateId(), registeredAt);
        return response(mandate, hash, "REGISTERED", registeredAt);
    }

    public String fingerprint(Ap2AuthorizationEnvelope envelope) {
        try {
            byte[] bytes = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(envelope);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint authorization envelope.", exception);
        }
    }

    private RegisterMandateRequest toGuardRequest(Mandate mandate, String hash) {
        var evidence = mandate.authorizationEvidence();
        var constraints = evidence.normalizedConstraints();
        return new RegisterMandateRequest(mandate.mandateId(), mandate.journeyId(), mandate.userId(),
                mandate.merchantId(), new RegisterMandateRequest.Authorization("SIMULATED_AP2",
                mandate.authorizationReference(), evidence.protocolVersion(), hash, "SIMULATED"),
                new RegisterMandateRequest.Constraints(constraints.maxAmount(), constraints.currency(),
                        constraints.allowedCategories(), constraints.shippingCountry(), constraints.allowedProductIds()),
                mandate.expiresAt());
    }

    private RegistrationResponse response(Mandate mandate, String hash, String status, Instant registeredAt) {
        return new RegistrationResponse(mandate.mandateId(), mandate.journeyId(),
                mandate.authorizationReference(), hash, "SIMULATED", status, registeredAt);
    }

    private void validate(Ap2AuthorizationEnvelope envelope) {
        if (envelope == null || !"mandatelens.ap2-envelope.v1".equals(envelope.envelopeVersion())
                || !"0.2".equals(envelope.protocolVersion()) || envelope.mode() == null
                || envelope.provenance() != Ap2AuthorizationEnvelope.Provenance.SIMULATED
                || blank(envelope.authorizationId()) || envelope.artifacts() == null || envelope.artifacts().isEmpty()
                || envelope.normalizedConstraints() == null || envelope.artifacts().isEmpty()) {
            throw new IllegalArgumentException("A complete simulated AP2 authorization envelope is required.");
        }
        if (envelope.artifacts().stream().anyMatch(artifact -> artifact == null
                || artifact.kind() == null || !expectedVct(artifact.kind()).equals(artifact.vct())
                || blank(artifact.artifactReference()) || blank(artifact.artifactHash())
                || artifact.verificationStatus() != Ap2AuthorizationEnvelope.VerificationStatus.SIMULATED
                || artifact.issuedAt() != null && artifact.issuedAt().isAfter(Instant.now())
                || artifact.expiresAt() != null && !artifact.expiresAt().isAfter(Instant.now()))) {
            throw new IllegalArgumentException("Each demo artifact must have a valid kind/vct, reference, hash, future expiry, and SIMULATED verification status.");
        }
        if (envelope.artifacts().stream().anyMatch(artifact ->
                artifact.kind() == Ap2AuthorizationEnvelope.ArtifactKind.CLOSED_CHECKOUT
                        && blank(artifact.checkoutHash()))) {
            throw new IllegalArgumentException("A closed Checkout Mandate artifact must include its checkoutHash.");
        }
        var constraints = envelope.normalizedConstraints();
        if (constraints.maxAmount() == null || constraints.maxAmount().signum() < 0
                || blank(constraints.currency()) || constraints.currency().length() != 3
                || constraints.allowedCategories().stream().anyMatch(MandateRegistrationService::blank)
                || constraints.allowedProductIds().stream().anyMatch(MandateRegistrationService::blank)) {
            throw new IllegalArgumentException("The demo authorization must include a maximum amount and currency.");
        }
    }

    private String expectedVct(Ap2AuthorizationEnvelope.ArtifactKind kind) {
        return switch (kind) {
            case OPEN_CHECKOUT -> "mandate.checkout.open.1";
            case CLOSED_CHECKOUT -> "mandate.checkout.1";
            case OPEN_PAYMENT -> "mandate.payment.open.1";
            case CLOSED_PAYMENT -> "mandate.payment.1";
        };
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record RegistrationRequest(String mandateId, String journeyId, String userId, String merchantId,
                                     Instant createdAt, Instant expiresAt,
                                     Ap2AuthorizationEnvelope authorization) { }
    public record RegistrationResponse(String mandateId, String journeyId, String authorizationId,
                                       String authorizationHash, String verificationStatus,
                                       String agentGuardRegistrationStatus, Instant registeredAt) { }
}
