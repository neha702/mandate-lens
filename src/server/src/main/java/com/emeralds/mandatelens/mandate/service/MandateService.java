package com.emeralds.mandatelens.mandate.service;

import com.emeralds.mandatelens.mandate.model.Mandate;
import com.emeralds.mandatelens.mandate.model.Ap2AuthorizationEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** PostgreSQL-backed mandate persistence. The bundled mandate remains clearly simulated demo data. */
@Service
public class MandateService implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BigDecimal demoMaxAmount;

    public MandateService(JdbcTemplate jdbc, ObjectMapper mapper,
                          @Value("${agentguard.demo.max-amount:600.00}") String demoMaxAmount) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.demoMaxAmount = new BigDecimal(demoMaxAmount);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant created = Instant.parse("2026-10-20T08:55:00Z");
        Instant expires = Instant.parse("2026-11-20T08:55:00Z");
        Ap2AuthorizationEnvelope evidence = new Ap2AuthorizationEnvelope("mandatelens.ap2-envelope.v1", "0.2",
                Ap2AuthorizationEnvelope.Mode.AUTONOMOUS, Ap2AuthorizationEnvelope.Provenance.SIMULATED,
                "demo-ap2-auth-shoes-001", List.of(
                new Ap2AuthorizationEnvelope.Artifact(Ap2AuthorizationEnvelope.ArtifactKind.OPEN_CHECKOUT,
                        "mandate.checkout.open.1", "fixture:demo-ap2-auth-shoes-001:open-checkout",
                        "simulated-hash-open-checkout-001", null,
                        Ap2AuthorizationEnvelope.VerificationStatus.SIMULATED, created, expires,
                        java.util.Map.of("merchant", "STRIDEHUB", "allowedCategories", List.of("RUNNING_SHOES"),
                                "maxAmount", demoMaxAmount.toPlainString(), "currency", "USD", "shippingCountry", "US"))),
                new Ap2AuthorizationEnvelope.NormalizedConstraints(demoMaxAmount, "USD",
                        List.of("RUNNING_SHOES"), "US", List.of()));
        Mandate demo = new Mandate("mandate-demo-shoes-001", "demo-shoes-001", "demo-shopper-001",
                "STRIDEHUB", evidence.authorizationId(), demoMaxAmount, "USD",
                List.of("RUNNING_SHOES"), List.of(), "US", created, expires, Mandate.Source.SIMULATED_AP2,
                evidence, null);
        save(demo);
    }

    public Optional<Mandate> findByJourneyId(String journeyId) {
        return jdbc.query("""
                SELECT m.mandate_id, j.journey_id, m.user_ref, m.merchant_id, m.authorization_reference,
                       m.max_amount,
                       m.currency, m.allowed_categories, m.allowed_product_ids, m.shipping_country, m.created_at, m.expires_at,
                       m.authorization_source, m.authorization_evidence::text, m.agentguard_registered_at
                FROM trust.trust_journeys j JOIN trust.mandates m ON m.mandate_id = j.mandate_id
                WHERE j.journey_id = ? AND m.status = 'ACTIVE'
                """, rs -> rs.next() ? Optional.of(new Mandate(rs.getString("mandate_id"),
                rs.getString("journey_id"), rs.getString("user_ref"), rs.getString("merchant_id"),
                rs.getString("authorization_reference"),
                rs.getBigDecimal("max_amount"), rs.getString("currency") == null ? null : rs.getString("currency").trim(),
                rs.getArray("allowed_categories") == null ? List.of()
                        : List.of((String[]) rs.getArray("allowed_categories").getArray()),
                rs.getArray("allowed_product_ids") == null ? List.of()
                        : List.of((String[]) rs.getArray("allowed_product_ids").getArray()),
                rs.getString("shipping_country"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                "SIMULATED_AP2".equals(rs.getString("authorization_source"))
                        ? Mandate.Source.SIMULATED_AP2 : Mandate.Source.SIMULATED_AP2,
                readEvidence(rs.getString("authorization_evidence")),
                rs.getTimestamp("agentguard_registered_at") == null ? null
                        : rs.getTimestamp("agentguard_registered_at").toInstant())) : Optional.empty(), journeyId);
    }

    @Transactional
    public void save(Mandate mandate) {
        jdbc.update("""
                INSERT INTO trust.mandates
                (mandate_id, merchant_id, user_ref, authorization_source, authorization_reference, status,
                 max_amount, currency, allowed_categories, allowed_product_ids, shipping_country,
                 expires_at, authorization_evidence)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, CAST(? AS text[]), CAST(? AS text[]), ?, ?, CAST(? AS jsonb))
                ON CONFLICT (mandate_id) DO UPDATE SET user_ref = EXCLUDED.user_ref,
                    merchant_id = EXCLUDED.merchant_id, max_amount = EXCLUDED.max_amount,
                    currency = EXCLUDED.currency, allowed_categories = EXCLUDED.allowed_categories,
                    allowed_product_ids = EXCLUDED.allowed_product_ids,
                    shipping_country = EXCLUDED.shipping_country, expires_at = EXCLUDED.expires_at,
                    authorization_source = EXCLUDED.authorization_source,
                    authorization_reference = EXCLUDED.authorization_reference,
                    agentguard_registered_at = CASE
                        WHEN trust.mandates.authorization_evidence IS DISTINCT FROM EXCLUDED.authorization_evidence
                          OR trust.mandates.authorization_reference IS DISTINCT FROM EXCLUDED.authorization_reference
                          OR trust.mandates.merchant_id IS DISTINCT FROM EXCLUDED.merchant_id
                          OR trust.mandates.max_amount IS DISTINCT FROM EXCLUDED.max_amount
                          OR trust.mandates.currency IS DISTINCT FROM EXCLUDED.currency
                          OR trust.mandates.allowed_categories IS DISTINCT FROM EXCLUDED.allowed_categories
                          OR trust.mandates.allowed_product_ids IS DISTINCT FROM EXCLUDED.allowed_product_ids
                          OR trust.mandates.shipping_country IS DISTINCT FROM EXCLUDED.shipping_country
                          OR trust.mandates.expires_at IS DISTINCT FROM EXCLUDED.expires_at
                        THEN NULL ELSE trust.mandates.agentguard_registered_at END,
                    authorization_evidence = EXCLUDED.authorization_evidence,
                    updated_at = NOW()
                """, mandate.mandateId(), mandate.merchantId(), mandate.userId(),
                mandate.source() == null ? "SIMULATED_AP2" : mandate.source().name(),
                mandate.authorizationReference(), mandate.maxAmount(), mandate.currency(),
                new SqlArrayValue("text", mandate.allowedCategories().toArray()),
                new SqlArrayValue("text", mandate.allowedProductIds().toArray()), mandate.shippingCountry(),
                mandate.expiresAt() == null ? null : Timestamp.from(mandate.expiresAt()),
                writeEvidence(mandate.authorizationEvidence()));
        jdbc.update("""
                INSERT INTO trust.trust_journeys (journey_id, mandate_id, merchant_id, status, started_at)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (journey_id) DO UPDATE SET mandate_id = EXCLUDED.mandate_id,
                    merchant_id = EXCLUDED.merchant_id, updated_at = NOW()
                """, mandate.journeyId(), mandate.mandateId(), mandate.merchantId(),
                Timestamp.from(mandate.createdAt() == null ? Instant.now() : mandate.createdAt()));
    }

    public void markAgentGuardRegistered(String mandateId, Instant at) {
        jdbc.update("UPDATE trust.mandates SET agentguard_registered_at = ?, updated_at = NOW() " +
                "WHERE mandate_id = ?", Timestamp.from(at), mandateId);
    }

    private String writeEvidence(Ap2AuthorizationEnvelope evidence) {
        if (evidence == null) return "{}";
        try { return mapper.writeValueAsString(evidence); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not encode AP2 authorization evidence.", exception);
        }
    }

    private Ap2AuthorizationEnvelope readEvidence(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return null;
        try { return mapper.readValue(json, Ap2AuthorizationEnvelope.class); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read AP2 authorization evidence.", exception);
        }
    }
}
