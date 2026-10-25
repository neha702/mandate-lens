ALTER TABLE trust.mandates
    ADD COLUMN agentguard_registered_at TIMESTAMPTZ,
    ADD COLUMN allowed_product_ids TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_mandates_guard_registration
    ON trust.mandates(agentguard_registered_at)
    WHERE status = 'ACTIVE' AND agentguard_registered_at IS NULL;
