-- =====================================================
-- SCHEMAS
-- =====================================================

CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS trust;


-- =====================================================
-- MERCHANT SCHEMA
-- =====================================================

-- 1. PRODUCTS
-- Source of truth for the merchant product catalog.
-- product_id should match the relevant catalog/variant ID
-- used in the Store Sync product feed.

CREATE TABLE merchant.products (
    product_id TEXT PRIMARY KEY,
    sku TEXT UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    product_url TEXT,
    image_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    available_quantity INTEGER NOT NULL DEFAULT 10 CHECK (available_quantity >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category
    ON merchant.products(category);

CREATE INDEX idx_products_active
    ON merchant.products(is_active);


-- 2. CARTS
-- Current cart state belongs to the merchant.
-- mandatelens_journey_id correlates a cart with its
-- MandateLens trust journey. It is not a foreign key.

CREATE TABLE merchant.carts (
    cart_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    mandatelens_journey_id TEXT,
    source_channel TEXT NOT NULL DEFAULT 'STORESYNC',
    customer_ref TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (
            status IN (
                'OPEN',
                'CHECKOUT_STARTED',
                'COMPLETED',
                'ABANDONED',
                'EXPIRED'
            )
        ),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    shipping_country CHAR(2),
    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (subtotal >= 0),
    shipping_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (shipping_amount >= 0),
    tax_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (tax_amount >= 0),
    total NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_carts_journey
    ON merchant.carts(mandatelens_journey_id);


-- 3. CART ITEMS
-- Preserve price and category snapshots so evaluations
-- do not depend exclusively on the current catalog.

CREATE TABLE merchant.cart_items (
    cart_item_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    cart_id TEXT NOT NULL
        REFERENCES merchant.carts(cart_id),
    product_id TEXT NOT NULL
        REFERENCES merchant.products(product_id),
    product_name_snapshot TEXT NOT NULL,
    category_snapshot TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
        CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart
    ON merchant.cart_items(cart_id);


-- 4. ORDERS
-- The merchant's current order state.

CREATE TABLE merchant.orders (
    order_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    cart_id TEXT NOT NULL
        REFERENCES merchant.carts(cart_id),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (
            status IN (
                'PENDING',
                'APPROVED',
                'PROCESSING',
                'COMPLETED',
                'CANCELLED',
                'FAILED'
            )
        ),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    shipping_country CHAR(2),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    shipping_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (shipping_amount >= 0),
    tax_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (tax_amount >= 0),
    total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_cart
    ON merchant.orders(cart_id);

CREATE INDEX idx_orders_status
    ON merchant.orders(status);


-- 5. ORDER ITEMS
-- Historical item snapshots for each order.

CREATE TABLE merchant.order_items (
    order_item_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    order_id TEXT NOT NULL
        REFERENCES merchant.orders(order_id),
    product_id TEXT,
    product_name_snapshot TEXT NOT NULL,
    category_snapshot TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
        CHECK (unit_price >= 0)
);


-- 6. CHECKOUT SESSIONS
-- Records the operational state of checkout attempts.
-- A blocked attempt may have a checkout session but must
-- not proceed to payment execution.

CREATE TABLE merchant.checkout_sessions (
    checkout_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    cart_id TEXT NOT NULL
        REFERENCES merchant.carts(cart_id),
    order_id TEXT
        REFERENCES merchant.orders(order_id),
    mandatelens_journey_id TEXT,
    guard_decision_id TEXT,
    status TEXT NOT NULL DEFAULT 'INITIATED'
        CHECK (
            status IN (
                'INITIATED',
                'VALIDATING',
                'BLOCKED',
                'APPROVED',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkout_cart
    ON merchant.checkout_sessions(cart_id);

CREATE INDEX idx_checkout_journey
    ON merchant.checkout_sessions(mandatelens_journey_id);


-- 7. PAYMENTS
-- The merchant owns the operational payment state.
-- Store PayPal references and payment lifecycle status,
-- not raw payment credentials.

CREATE TABLE merchant.payments (
    payment_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    order_id TEXT NOT NULL
        REFERENCES merchant.orders(order_id),
    provider TEXT NOT NULL DEFAULT 'PAYPAL',
    provider_order_id TEXT UNIQUE,
    provider_authorization_id TEXT UNIQUE,
    provider_capture_id TEXT UNIQUE,
    status TEXT NOT NULL DEFAULT 'CREATED'
        CHECK (
            status IN (
                'CREATED',
                'PENDING',
                'AUTHORIZED',
                'CAPTURE_PENDING',
                'CAPTURED',
                'DENIED',
                'FAILED',
                'REFUNDED'
            )
        ),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order
    ON merchant.payments(order_id);

CREATE INDEX idx_payments_status
    ON merchant.payments(status);


-- =====================================================
-- TRUST SCHEMA
-- =====================================================

-- 1. MANDATES
-- Store normalized authorization constraints and a
-- reference to verified AP2 authorization evidence.
-- Do not store secrets or bearer tokens here.

CREATE TABLE trust.mandates (
    mandate_id TEXT PRIMARY KEY,
    merchant_id TEXT NOT NULL,
    user_ref TEXT,
    authorization_source TEXT NOT NULL DEFAULT 'AP2',
    authorization_reference TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    max_amount NUMERIC(12, 2)
        CHECK (max_amount IS NULL OR max_amount >= 0),
    currency CHAR(3),
    allowed_categories TEXT[],
    shipping_country CHAR(2),
    expires_at TIMESTAMPTZ,
    authorization_evidence JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (authorization_source, authorization_reference)
);


-- 2. TRUST JOURNEYS
-- Each row represents one tracked agent-commerce journey.
-- A mandate may be absent if a journey is observed before
-- an authorization can be correlated.

CREATE TABLE trust.trust_journeys (
    journey_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    mandate_id TEXT
        REFERENCES trust.mandates(mandate_id),
    merchant_id TEXT NOT NULL,
    agent_id TEXT,
    external_session_id TEXT,
    cart_id TEXT,
    order_id TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (
            status IN (
                'ACTIVE',
                'COMPLETED',
                'BLOCKED',
                'FAILED',
                'ABANDONED',
                'EXPIRED'
            )
        ),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_event_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journeys_mandate
    ON trust.trust_journeys(mandate_id);

CREATE INDEX idx_journeys_merchant
    ON trust.trust_journeys(merchant_id);

CREATE INDEX idx_journeys_status
    ON trust.trust_journeys(status);


-- 3. GUARD DECISIONS
-- Preserve the result of every relevant AgentGuard
-- evaluation and the reasons supporting that result.

CREATE TABLE trust.guard_decisions (
    decision_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    journey_id TEXT NOT NULL
        REFERENCES trust.trust_journeys(journey_id),
    mandate_id TEXT NOT NULL
        REFERENCES trust.mandates(mandate_id),
    action_type TEXT NOT NULL,
    decision TEXT NOT NULL
        CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    reason_details JSONB NOT NULL DEFAULT '[]'::JSONB,
    action_context JSONB NOT NULL DEFAULT '{}'::JSONB,
    correlation_id TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guard_decisions_journey
    ON trust.guard_decisions(journey_id, evaluated_at);

CREATE INDEX idx_guard_decisions_outcome
    ON trust.guard_decisions(decision, evaluated_at);


-- 4. JOURNEY EVENTS
-- Append-only event history consumed by AgentPulse.
-- event_id must be stable across delivery retries so that
-- repeated submissions of the same event are idempotent.

CREATE TABLE trust.journey_events (
    event_id TEXT PRIMARY KEY,
    journey_id TEXT NOT NULL
        REFERENCES trust.trust_journeys(journey_id),
    event_type TEXT NOT NULL,
    source TEXT NOT NULL
        CHECK (
            source IN (
                'MERCHANT',
                'AGENT_GUARD',
                'PAYPAL_WEBHOOK',
                'SYSTEM'
            )
        ),
    entity_type TEXT,
    entity_id TEXT,
    outcome TEXT NOT NULL
        CHECK (
            outcome IN (
                'ATTEMPTED',
                'SUCCESS',
                'BLOCKED',
                'FAILED',
                'PENDING',
                'INFO'
            )
        ),
    guard_decision_id TEXT
        REFERENCES trust.guard_decisions(decision_id),
    correlation_id TEXT,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journey_events_timeline
    ON trust.journey_events(journey_id, occurred_at);

CREATE INDEX idx_journey_events_type
    ON trust.journey_events(event_type, occurred_at);

CREATE INDEX idx_journey_events_decision
    ON trust.journey_events(guard_decision_id);

-- Store Sync adapter details not represented by the canonical commerce tables.
CREATE TABLE merchant.store_sync_cart_state (
    cart_id TEXT PRIMARY KEY REFERENCES merchant.carts(cart_id),
    cart_details JSONB,
    guard_context JSONB,
    order_state JSONB,
    completed_response JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
