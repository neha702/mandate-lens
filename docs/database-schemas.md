# Mandate Lens Database Schemas

**Status:** Current schema reference. Flyway SQL under `src/server/src/main/resources/db/migration/` is authoritative.

The database has two PostgreSQL schemas:

| Schema | Owns |
|---|---|
| `merchant` | Catalog, cart, order, payment, and Store Sync adapter state. |
| `trust` | Mandates, journeys, Guard decisions, operation state, callbacks, and journey events. |

The application migrations create 14 domain tables: 8 in `merchant` and 6 in `trust`. Flyway's own migration-history table is maintained separately in `trust`.

Flyway is configured with `trust` as its default schema, so its migration history is maintained in `trust.flyway_schema_history`. The migrations create no application tables in `public`.

The names reflect data ownership. Cart lines remain merchant data; authorization evidence and audit history remain trust data. The AP2-style envelope is stored in `trust.mandates.authorization_evidence` JSONB. There is no separate AP2 artifact table and cart lines are not duplicated inside the envelope.

## Migration history

| Migration | Changes |
|---|---|
| `V1__initial_schema.sql` | Creates `merchant` and `trust`, initial commerce/trust tables, indexes, and Store Sync cart state. |
| `V2__agentpulse_operations.sql` | Adds pending operation and callback deduplication tables; adds `EMPTY` to journey statuses. |
| `V3__ap2_authorization_envelope.sql` | Adds `trust.mandates.allowed_product_ids` and `agentguard_registered_at`, plus an index for active mandates awaiting Guard registration. |

## `merchant` tables

### `merchant.products`

Catalog source of truth. `product_id` is the Store Sync product/variant identifier.

| Column | Type / rule |
|---|---|
| `product_id` | `TEXT`, primary key |
| `sku` | `TEXT`, nullable, unique |
| `name` | `TEXT NOT NULL` |
| `description` | `TEXT`, nullable |
| `category` | `TEXT NOT NULL` |
| `price` | `NUMERIC(12,2) NOT NULL`, `>= 0` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'USD'` |
| `product_url`, `image_url` | `TEXT`, nullable |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `available_quantity` | `INTEGER NOT NULL DEFAULT 10`, `>= 0` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: category and active status.

### `merchant.carts`

Current merchant cart aggregate. `mandatelens_journey_id` is a correlation value, not a foreign key.

| Column | Type / rule |
|---|---|
| `cart_id` | `TEXT` primary key, generated UUID by default |
| `mandatelens_journey_id` | `TEXT`, nullable |
| `source_channel` | `TEXT NOT NULL DEFAULT 'STORESYNC'` |
| `customer_ref` | `TEXT`, nullable |
| `status` | `TEXT NOT NULL DEFAULT 'OPEN'`; `OPEN`, `CHECKOUT_STARTED`, `COMPLETED`, `ABANDONED`, `EXPIRED` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'USD'` |
| `shipping_country` | `CHAR(2)`, nullable |
| `subtotal`, `shipping_amount`, `tax_amount`, `total` | `NUMERIC(12,2) NOT NULL`; defaults are 0; all must be `>= 0` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Index: `mandatelens_journey_id`.

### `merchant.cart_items`

Current item lines and catalog snapshots for one cart.

| Column | Type / rule |
|---|---|
| `cart_item_id` | `TEXT` primary key, generated UUID by default |
| `cart_id` | `TEXT NOT NULL`, FK to `merchant.carts(cart_id)` |
| `product_id` | `TEXT NOT NULL`, FK to `merchant.products(product_id)` |
| `product_name_snapshot`, `category_snapshot` | `TEXT NOT NULL` |
| `quantity` | `INTEGER NOT NULL`, `> 0` |
| `unit_price` | `NUMERIC(12,2) NOT NULL`, `>= 0` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Unique constraint: `(cart_id, product_id)`. Index: `cart_id`.

### `merchant.orders`

Merchant order total/status snapshot associated with a cart.

| Column | Type / rule |
|---|---|
| `order_id` | `TEXT` primary key, generated UUID by default |
| `cart_id` | `TEXT NOT NULL`, FK to `merchant.carts(cart_id)` |
| `status` | `TEXT NOT NULL DEFAULT 'PENDING'`; `PENDING`, `APPROVED`, `PROCESSING`, `COMPLETED`, `CANCELLED`, `FAILED` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'USD'` |
| `shipping_country` | `CHAR(2)`, nullable |
| `subtotal`, `shipping_amount`, `tax_amount`, `total` | `NUMERIC(12,2) NOT NULL`, non-negative; shipping/tax default to 0 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: `cart_id`, `status`.

### `merchant.order_items`

Historical order line snapshots.

| Column | Type / rule |
|---|---|
| `order_item_id` | `TEXT` primary key, generated UUID by default |
| `order_id` | `TEXT NOT NULL`, FK to `merchant.orders(order_id)` |
| `product_id` | `TEXT`, nullable; no product FK so historical rows can outlive catalog changes |
| `product_name_snapshot`, `category_snapshot` | `TEXT NOT NULL` |
| `quantity` | `INTEGER NOT NULL`, `> 0` |
| `unit_price` | `NUMERIC(12,2) NOT NULL`, `>= 0` |

### `merchant.checkout_sessions`

Operational checkout attempts. The journey and Guard decision references are correlation values, not foreign keys.

| Column | Type / rule |
|---|---|
| `checkout_id` | `TEXT` primary key, generated UUID by default |
| `cart_id` | `TEXT NOT NULL`, FK to `merchant.carts(cart_id)` |
| `order_id` | `TEXT`, nullable, FK to `merchant.orders(order_id)` |
| `mandatelens_journey_id`, `guard_decision_id` | `TEXT`, nullable |
| `status` | `TEXT NOT NULL DEFAULT 'INITIATED'`; `INITIATED`, `VALIDATING`, `BLOCKED`, `APPROVED`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: cart and Mandate Lens journey IDs.

### `merchant.payments`

Payment lifecycle and safe provider references; no raw payment credentials.

| Column | Type / rule |
|---|---|
| `payment_id` | `TEXT` primary key, generated UUID by default |
| `order_id` | `TEXT NOT NULL`, FK to `merchant.orders(order_id)` |
| `provider` | `TEXT NOT NULL DEFAULT 'PAYPAL'` |
| `provider_order_id`, `provider_authorization_id`, `provider_capture_id` | `TEXT`, nullable, each unique |
| `status` | `TEXT NOT NULL DEFAULT 'CREATED'`; `CREATED`, `PENDING`, `AUTHORIZED`, `CAPTURE_PENDING`, `CAPTURED`, `DENIED`, `FAILED`, `REFUNDED` |
| `amount` | `NUMERIC(12,2) NOT NULL`, `>= 0` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'USD'` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: order and status.

### `merchant.store_sync_cart_state`

Store Sync-specific adapter state that is not part of canonical cart/order tables.

| Column | Type / rule |
|---|---|
| `cart_id` | `TEXT` primary key, FK to `merchant.carts(cart_id)` |
| `cart_details`, `guard_context`, `order_state`, `completed_response` | `JSONB`, nullable |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

## `trust` tables

### `trust.mandates`

Normalized policy plus the complete simulated authorization envelope in JSONB.

| Column | Type / rule |
|---|---|
| `mandate_id` | `TEXT` primary key |
| `merchant_id` | `TEXT NOT NULL` |
| `user_ref` | `TEXT`, nullable |
| `authorization_source` | `TEXT NOT NULL DEFAULT 'AP2'`; demo writes `SIMULATED_AP2` |
| `authorization_reference` | `TEXT`, nullable |
| `status` | `TEXT NOT NULL DEFAULT 'ACTIVE'`; `ACTIVE`, `REVOKED`, `EXPIRED` |
| `max_amount` | `NUMERIC(12,2)`, nullable, if set `>= 0` |
| `currency` | `CHAR(3)`, nullable |
| `allowed_categories` | `TEXT[]`, nullable |
| `allowed_product_ids` | `TEXT[] NOT NULL DEFAULT '{}'` (V3) |
| `shipping_country` | `CHAR(2)`, nullable |
| `expires_at` | `TIMESTAMPTZ`, nullable |
| `authorization_evidence` | `JSONB NOT NULL DEFAULT '{}'` |
| `agentguard_registered_at` | `TIMESTAMPTZ`, nullable (V3; cleared if registered evidence/policy fields change) |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Unique constraint: `(authorization_source, authorization_reference)`. V3 index: active mandates with no Guard registration timestamp.

### `trust.trust_journeys`

One row for a shopping journey. `cart_id` and `order_id` are correlation values, not foreign keys.

| Column | Type / rule |
|---|---|
| `journey_id` | `TEXT` primary key, generated UUID by default |
| `mandate_id` | `TEXT`, nullable, FK to `trust.mandates(mandate_id)` |
| `merchant_id` | `TEXT NOT NULL` |
| `agent_id`, `external_session_id`, `cart_id`, `order_id` | `TEXT`, nullable |
| `status` | `TEXT NOT NULL DEFAULT 'ACTIVE'`; `ACTIVE`, `EMPTY` (V2), `COMPLETED`, `BLOCKED`, `FAILED`, `ABANDONED`, `EXPIRED` |
| `started_at`, `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |
| `last_event_at`, `ended_at` | `TIMESTAMPTZ`, nullable |

Indexes: mandate, merchant, status.

### `trust.guard_decisions`

Guard outcome and structured explanation/context.

| Column | Type / rule |
|---|---|
| `decision_id` | `TEXT` primary key, generated UUID by default |
| `journey_id` | `TEXT NOT NULL`, FK to `trust.trust_journeys(journey_id)` |
| `mandate_id` | `TEXT NOT NULL`, FK to `trust.mandates(mandate_id)` |
| `action_type` | `TEXT NOT NULL` |
| `decision` | `TEXT NOT NULL`; `ALLOW`, `REVIEW`, `BLOCK` |
| `reason_details` | `JSONB NOT NULL DEFAULT '[]'` |
| `action_context` | `JSONB NOT NULL DEFAULT '{}'` |
| `correlation_id` | `TEXT`, nullable; operation ID in the current flow |
| `evaluated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: `(journey_id, evaluated_at)` and `(decision, evaluated_at)`.

### `trust.journey_events`

Append-only timeline. Event IDs are the primary-key deduplication key.

| Column | Type / rule |
|---|---|
| `event_id` | `TEXT` primary key |
| `journey_id` | `TEXT NOT NULL`, FK to `trust.trust_journeys(journey_id)` |
| `event_type` | `TEXT NOT NULL` |
| `source` | `TEXT NOT NULL`; `MERCHANT`, `AGENT_GUARD`, `PAYPAL_WEBHOOK`, `SYSTEM` |
| `entity_type`, `entity_id` | `TEXT`, nullable |
| `outcome` | `TEXT NOT NULL`; `ATTEMPTED`, `SUCCESS`, `BLOCKED`, `FAILED`, `PENDING`, `INFO` |
| `guard_decision_id` | `TEXT`, nullable, FK to `trust.guard_decisions(decision_id)` |
| `correlation_id` | `TEXT`, nullable |
| `details` | `JSONB NOT NULL DEFAULT '{}'` |
| `occurred_at` | `TIMESTAMPTZ NOT NULL` |
| `received_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

Indexes: `(journey_id, occurred_at)`, `(event_type, occurred_at)`, and `guard_decision_id`.

### `trust.pending_operations` (V2)

Guard check lifecycle and merchant result callback state.

| Column | Type / rule |
|---|---|
| `operation_id` | `TEXT` primary key |
| `journey_id` | `TEXT NOT NULL`, FK to `trust.trust_journeys(journey_id)` |
| `merchant_id`, `cart_id`, `action_type` | `TEXT NOT NULL` |
| `status` | `TEXT NOT NULL`; `PENDING_CHECK`, `CHECK_FAILED`, `AWAITING_MERCHANT_RESULT`, `SUCCEEDED`, `FAILED`, `NOT_APPLIED`, `TIMED_OUT` |
| `idempotency_key`, `request_hash` | `TEXT NOT NULL` |
| `request_context` | `JSONB NOT NULL DEFAULT '{}'` |
| `guard_decision_id` | `TEXT`, nullable, FK to `trust.guard_decisions(decision_id)` |
| `result_event_id` | `TEXT`, nullable |
| `created_at`, `result_due_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` / required due time |
| `resolved_at` | `TIMESTAMPTZ`, nullable |

Unique constraint: `(merchant_id, idempotency_key)`. Indexes: `(journey_id, created_at)` and `(status, result_due_at)`.

### `trust.merchant_callback_events` (V2)

Stores callback payloads for retry deduplication.

| Column | Type / rule |
|---|---|
| `event_id` | `TEXT` primary key |
| `operation_id` | `TEXT NOT NULL`, FK to `trust.pending_operations(operation_id)` |
| `payload` | `JSONB NOT NULL` |
| `received_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` |

## Relationship summary

```text
merchant.products ──< merchant.cart_items >── merchant.carts ──< merchant.orders ──< merchant.order_items
                                                   ├──────────< merchant.checkout_sessions
                                                   ├──────────< merchant.store_sync_cart_state
                                                   └──────────< merchant.payments (via orders)

trust.mandates ──< trust.trust_journeys ──< trust.guard_decisions ──< trust.journey_events
                         ├──< trust.pending_operations ──< trust.merchant_callback_events
                         └──< trust.journey_events
```

Some correlation fields intentionally do not have foreign keys: for example, the merchant cart's Mandate Lens journey ID and the journey's current cart/order IDs. Confirm before adding constraints because lifecycle retention and cross-schema ownership are deliberate.

## Operational notes

- The local Store Sync simulator currently writes current cart, order, payment references, and adapter JSON state; historical `order_items` and `checkout_sessions` are part of the schema but are not the simulator's primary runtime path.
- The `V1` SQL comment describes evidence as verified AP2 evidence. The current hackathon code stores explicitly simulated evidence; do not treat the comment or JSONB value as proof of cryptographic verification.
- Use Flyway migrations as the source of truth. Do not edit an already applied migration to change a deployed database; add a new migration instead.
