CREATE TABLE trust.pending_operations (
    operation_id TEXT PRIMARY KEY,
    journey_id TEXT NOT NULL REFERENCES trust.trust_journeys(journey_id),
    merchant_id TEXT NOT NULL,
    cart_id TEXT NOT NULL,
    action_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN (
        'PENDING_CHECK', 'CHECK_FAILED', 'AWAITING_MERCHANT_RESULT', 'SUCCEEDED', 'FAILED',
        'NOT_APPLIED', 'TIMED_OUT'
    )),
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    request_context JSONB NOT NULL DEFAULT '{}'::JSONB,
    guard_decision_id TEXT REFERENCES trust.guard_decisions(decision_id),
    result_event_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    result_due_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_pending_operations_journey
    ON trust.pending_operations(journey_id, created_at);

CREATE INDEX idx_pending_operations_due
    ON trust.pending_operations(status, result_due_at);

CREATE TABLE trust.merchant_callback_events (
    event_id TEXT PRIMARY KEY,
    operation_id TEXT NOT NULL REFERENCES trust.pending_operations(operation_id),
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE trust.trust_journeys DROP CONSTRAINT IF EXISTS trust_journeys_status_check;
ALTER TABLE trust.trust_journeys ADD CONSTRAINT trust_journeys_status_check
    CHECK (status IN ('ACTIVE', 'EMPTY', 'COMPLETED', 'BLOCKED', 'FAILED', 'ABANDONED', 'EXPIRED'));
