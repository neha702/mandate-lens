package com.emeralds.mandatelens.mandate.service;

import com.emeralds.mandatelens.mandate.model.Mandate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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

    public MandateService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Mandate demo = new Mandate("mandate-demo-shoes-001", "demo-shoes-001", "demo-shopper-001",
                "STRIDEHUB", new BigDecimal("200.00"), "USD", List.of("RUNNING_SHOES"), "US",
                Instant.parse("2026-10-20T08:55:00Z"), Instant.parse("2026-11-20T08:55:00Z"),
                Mandate.Source.SIMULATED_AP2);
        save(demo);
    }

    public Optional<Mandate> findByJourneyId(String journeyId) {
        return jdbc.query("""
                SELECT m.mandate_id, j.journey_id, m.user_ref, m.merchant_id, m.max_amount,
                       m.currency, m.allowed_categories, m.shipping_country, m.created_at, m.expires_at,
                       m.authorization_source
                FROM trust.trust_journeys j JOIN trust.mandates m ON m.mandate_id = j.mandate_id
                WHERE j.journey_id = ? AND m.status = 'ACTIVE'
                """, rs -> rs.next() ? Optional.of(new Mandate(rs.getString("mandate_id"),
                rs.getString("journey_id"), rs.getString("user_ref"), rs.getString("merchant_id"),
                rs.getBigDecimal("max_amount"), rs.getString("currency") == null ? null : rs.getString("currency").trim(),
                rs.getArray("allowed_categories") == null ? List.of()
                        : List.of((String[]) rs.getArray("allowed_categories").getArray()),
                rs.getString("shipping_country"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                "SIMULATED_AP2".equals(rs.getString("authorization_source"))
                        ? Mandate.Source.SIMULATED_AP2 : Mandate.Source.SIMULATED_AP2)) : Optional.empty(), journeyId);
    }

    @Transactional
    public void save(Mandate mandate) {
        jdbc.update("""
                INSERT INTO trust.mandates
                (mandate_id, merchant_id, user_ref, authorization_source, status, max_amount, currency,
                 allowed_categories, shipping_country, expires_at)
                VALUES (?, ?, ?, 'SIMULATED_AP2', 'ACTIVE', ?, ?, ?::text[], ?, ?)
                ON CONFLICT (mandate_id) DO UPDATE SET user_ref = EXCLUDED.user_ref,
                    merchant_id = EXCLUDED.merchant_id, max_amount = EXCLUDED.max_amount,
                    currency = EXCLUDED.currency, allowed_categories = EXCLUDED.allowed_categories,
                    shipping_country = EXCLUDED.shipping_country, expires_at = EXCLUDED.expires_at,
                    updated_at = NOW()
                """, mandate.mandateId(), mandate.merchantId(), mandate.userId(), mandate.maxAmount(),
                mandate.currency(), new SqlArrayValue("text", mandate.allowedCategories().toArray()), mandate.shippingCountry(),
                mandate.expiresAt() == null ? null : Timestamp.from(mandate.expiresAt()));
        jdbc.update("""
                INSERT INTO trust.trust_journeys (journey_id, mandate_id, merchant_id, status, started_at)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (journey_id) DO UPDATE SET mandate_id = EXCLUDED.mandate_id,
                    merchant_id = EXCLUDED.merchant_id, updated_at = NOW()
                """, mandate.journeyId(), mandate.mandateId(), mandate.merchantId(),
                Timestamp.from(mandate.createdAt() == null ? Instant.now() : mandate.createdAt()));
    }
}
