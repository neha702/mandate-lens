-- AgentPulse records authorization registration as an explicit journey activity,
-- and distinguishes an allowed Guard check from informational events.
ALTER TABLE trust.journey_events
    DROP CONSTRAINT journey_events_source_check,
    ADD CONSTRAINT journey_events_source_check
        CHECK (source IN ('MERCHANT', 'AGENT_GUARD', 'PAYPAL_WEBHOOK', 'SYSTEM', 'MANDATE_LENS'));

ALTER TABLE trust.journey_events
    DROP CONSTRAINT journey_events_outcome_check,
    ADD CONSTRAINT journey_events_outcome_check
        CHECK (outcome IN ('ATTEMPTED', 'SUCCESS', 'ALLOWED', 'BLOCKED', 'FAILED', 'PENDING', 'INFO'));
