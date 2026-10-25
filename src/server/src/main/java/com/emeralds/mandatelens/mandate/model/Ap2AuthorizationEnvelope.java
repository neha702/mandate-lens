package com.emeralds.mandatelens.mandate.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Versioned transport/storage envelope for AP2 evidence; demo artifacts are explicitly simulated. */
public record Ap2AuthorizationEnvelope(
        String envelopeVersion,
        String protocolVersion,
        Mode mode,
        Provenance provenance,
        String authorizationId,
        List<Artifact> artifacts,
        NormalizedConstraints normalizedConstraints
) {
    public Ap2AuthorizationEnvelope {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public enum Mode { HUMAN_PRESENT, AUTONOMOUS }
    public enum Provenance { SIMULATED, VERIFIED }
    public enum ArtifactKind { OPEN_CHECKOUT, CLOSED_CHECKOUT, OPEN_PAYMENT, CLOSED_PAYMENT }
    public enum VerificationStatus { SIMULATED, VERIFIED, REJECTED, NOT_YET_AVAILABLE }

    public record Artifact(ArtifactKind kind, String vct, String artifactReference,
                           String artifactHash, String checkoutHash,
                           VerificationStatus verificationStatus,
                           Instant issuedAt, Instant expiresAt,
                           Map<String, Object> disclosedClaims) {
        public Artifact {
            disclosedClaims = disclosedClaims == null ? Map.of() : Map.copyOf(disclosedClaims);
        }
    }

    /** Mandate Lens' policy projection; fields may be AP2 claims or demo policy extensions. */
    public record NormalizedConstraints(BigDecimal maxAmount, String currency,
                                         List<String> allowedCategories, String shippingCountry,
                                         List<String> allowedProductIds) {
        public NormalizedConstraints {
            allowedCategories = allowedCategories == null ? List.of() : List.copyOf(allowedCategories);
            allowedProductIds = allowedProductIds == null ? List.of() : List.copyOf(allowedProductIds);
        }
    }
}
