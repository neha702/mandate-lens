# Mandate Lens API Contracts

**Status:** Current implementation reference. The Spring controllers and DTO records are authoritative if a field changes.

This document covers APIs served by Mandate Lens, its local Store Sync simulator, and the outbound AgentGuard contract. The Store Sync routes are demo-only and are not a production Store Sync authentication implementation.

## 1. API inventory

| Method | Path | Scope | Purpose |
|---|---|---|---|
| `GET` | `/api/health` | Mandate Lens | Health response. |
| `GET` | `/api/products` | Merchant catalog | Product list for the storefront. |
| `GET` | `/api/journeys/{journeyId}` | Basic AgentPulse | Raw event timeline. |
| `POST` | `/api/journeys/{journeyId}/events` | Basic AgentPulse | Append a demo/system journey event. |
| `POST` | `/api/v1/authorizations` | Mandate Lens | Store a simulated AP2-style authorization envelope and register its normalized constraints. |
| `POST` | `/api/v1/merchant-operations/check` | Merchant API | Check a proposed whole cart before applying an action. |
| `POST` | `/api/v1/merchant-operations/{operationId}/result` | Merchant API | Report the actual cart, removal, or payment result. |
| `GET` | `/api/v1/journeys/{journeyId}?merchantId=...` | Merchant API | Read saved operations, decisions, and timeline for the supplied merchant. |
| `GET` | `/api/v1/journeys?merchantId=...` | Merchant API | List a merchant's recent journeys for the merchant Pulse dashboard (up to 100). |
| `POST` | `/merchant-cart` | Local Store Sync simulator | Create a demo merchant cart and PayPal order context. |
| `GET` | `/merchant-cart/{cartId}` | Local Store Sync simulator | Read a demo cart. |
| `PUT` | `/merchant-cart/{cartId}` | Local Store Sync simulator | Replace all cart items; empty `items` clears the cart. |
| `POST` | `/merchant-cart/{cartId}/checkout` | Local Store Sync simulator | Guard-check final cart and capture the PayPal order after `ALLOW`. |

## 2. Simulated AP2-style mandate contract

### Intake: `POST /api/v1/authorizations`

The request is a Mandate Lens wrapper, **not an AP2 wire-format credential**. It stores the mandate metadata, artifacts, and normalized constraints as one JSONB envelope.

```json
{
  "mandateId": "mandate-demo-shoes-001",
  "journeyId": "demo-shoes-001",
  "userId": "demo-shopper-001",
  "merchantId": "STRIDEHUB",
  "createdAt": "2026-10-20T08:55:00Z",
  "expiresAt": "2026-11-20T08:55:00Z",
  "authorization": {
    "envelopeVersion": "mandatelens.ap2-envelope.v1",
    "protocolVersion": "0.2",
    "mode": "AUTONOMOUS",
    "provenance": "SIMULATED",
    "authorizationId": "demo-ap2-auth-shoes-001",
    "artifacts": [
      {
        "kind": "OPEN_CHECKOUT",
        "vct": "mandate.checkout.open.1",
        "artifactReference": "fixture:demo-ap2-auth-shoes-001:open-checkout",
        "artifactHash": "simulated-hash-open-checkout-001",
        "checkoutHash": null,
        "verificationStatus": "SIMULATED",
        "issuedAt": "2026-10-20T08:55:00Z",
        "expiresAt": "2026-11-20T08:55:00Z",
        "disclosedClaims": {
          "merchant": "STRIDEHUB",
          "allowedCategories": ["RUNNING_SHOES"],
          "maxAmount": "200.00",
          "currency": "USD",
          "shippingCountry": "US"
        }
      }
    ],
    "normalizedConstraints": {
      "maxAmount": 200.00,
      "currency": "USD",
      "allowedCategories": ["RUNNING_SHOES"],
      "allowedProductIds": [],
      "shippingCountry": "US"
    }
  }
}
```

Root fields `mandateId`, `journeyId`, `userId`, `merchantId`, and `authorization` are required. `createdAt` and `expiresAt` are optional; an expiry, when supplied, must be in the future. The envelope requires version `mandatelens.ap2-envelope.v1`, protocol version `0.2`, a mode, a non-empty authorization ID, one or more artifacts, and normalized constraints. Only `provenance: SIMULATED` and artifact `verificationStatus: SIMULATED` are accepted in this demo.

Artifact kinds and required `vct` values:

| `kind` | Required `vct` |
|---|---|
| `OPEN_CHECKOUT` | `mandate.checkout.open.1` |
| `CLOSED_CHECKOUT` | `mandate.checkout.1` |
| `OPEN_PAYMENT` | `mandate.payment.open.1` |
| `CLOSED_PAYMENT` | `mandate.payment.1` |

Every artifact requires a reference and hash. A closed Checkout artifact also requires `checkoutHash`. Artifact expiry must be in the future when supplied. `normalizedConstraints.maxAmount` must be non-negative and currency must have three characters. Categories and product IDs may be empty lists.

Successful response (`200 OK`):

```json
{
  "mandateId": "mandate-demo-shoes-001",
  "journeyId": "demo-shoes-001",
  "authorizationId": "demo-ap2-auth-shoes-001",
  "authorizationHash": "<sha256-of-envelope>",
  "verificationStatus": "SIMULATED",
  "agentGuardRegistrationStatus": "REGISTERED",
  "registeredAt": "2026-10-25T09:30:00Z"
}
```

In this demo, `maxAmount` is the per-checkout policy ceiling, not an AP2 recurring-payment budget. AP2 distinguishes an amount range for a particular payment from a cumulative budget used with recurrence constraints. Category, product-ID, and shipping-country restrictions are Mandate Lens policy projection fields unless present in the source authorization claims. `issuedAt` cannot be in the future; supplied artifact/root expiries must be in the future.

Mandate Lens stores the complete envelope under `trust.mandates.authorization_evidence`. It sends AgentGuard the authorization reference and envelope hash plus normalized constraints; it does not send the raw envelope or a payment credential. Registration is marked and reused until the stored envelope or registered policy fields change. This demo does not verify AP2 signatures, issuers, or credentials.

AP2 v0.2 names the standard artifact types Checkout Mandate and Payment Mandate, including open and closed forms. PayPal's overview also uses Cart Mandate and Intent Mandate terminology. Those terms should not be presented as identical wire contracts. The current envelope is a simulated projection; for protocol details see the [AP2 specification](https://github.com/google-agentic-commerce/AP2/blob/main/docs/ap2/specification.md), [Checkout Mandate](https://github.com/google-agentic-commerce/AP2/blob/main/docs/ap2/checkout_mandate.md), [Payment Mandate](https://github.com/google-agentic-commerce/AP2/blob/main/docs/ap2/payment_mandate.md), and [PayPal's AP2 overview](https://developer.paypal.com/community/blog/PayPal-Agent-Payments-Protocol/).

## 3. Merchant operation API

### Check: `POST /api/v1/merchant-operations/check`

Checks `ADD_ITEM`, `UPDATE_CART`, or `CHECKOUT`. Send both the current and complete proposed cart; the total is the full cart total, not the delta. The shipping country is supplied at the request root.

```json
{
  "merchantId": "STRIDEHUB",
  "journeyId": "demo-shoes-001",
  "cartId": "CART-123",
  "action": "ADD_ITEM",
  "idempotencyKey": "storesync:CART-123:add:request-456",
  "currentCart": {
    "currency": "USD",
    "items": [],
    "total": 0.00
  },
  "proposedCart": {
    "currency": "USD",
    "items": [
      { "productId": "shoe-001", "category": "RUNNING_SHOES", "quantity": 1, "unitPrice": 179.00 }
    ],
    "total": 179.00
  },
  "shippingCountry": "US"
}
```

Response fields include the original Guard decision, reasons, rule results/findings, operation status, and authorization correlation:

```json
{
  "operationId": "op-123",
  "journeyId": "demo-shoes-001",
  "decisionId": "decision-456",
  "decision": "ALLOW",
  "reasons": [],
  "ruleResults": [
    { "ruleId": "budget", "result": "PASS", "message": "Within the configured limit." }
  ],
  "policyVersion": "guard-policy-v1",
  "evaluatedAt": "2026-10-25T09:31:00Z",
  "contextFingerprint": "<guard-cart-fingerprint>",
  "findings": [],
  "operationStatus": "AWAITING_MERCHANT_RESULT",
  "authorizationId": "demo-ap2-auth-shoes-001",
  "authorizationHash": "<sha256-of-envelope>",
  "verificationStatus": "SIMULATED"
}
```

`decision` is `ALLOW`, `REVIEW`, or `BLOCK`. Only `ALLOW` permits the merchant to attempt the action. `REVIEW` and `BLOCK` are stored with status `NOT_APPLIED`; the Store Sync simulator returns a conflict and does not apply the change. Repeating the same merchant/idempotency key and same body returns the stored operation; reusing the key with different data is a conflict.

### Result: `POST /api/v1/merchant-operations/{operationId}/result`

```json
{
  "eventId": "merchant-demo:event-789",
  "merchantId": "STRIDEHUB",
  "journeyId": "demo-shoes-001",
  "cartId": "CART-123",
  "eventType": "ITEM_REMOVAL_SUCCEEDED",
  "outcome": "SUCCESS",
  "occurredAt": "2026-10-25T09:32:00Z",
  "merchantReference": "cart-revision-2",
  "details": {
    "total": 0.00,
    "currency": "USD",
    "failureCode": null,
    "message": null,
    "paypalOrderId": null,
    "paypalCaptureId": null,
    "changedItems": [{ "productId": "shoe-001", "quantityDelta": -1 }]
  }
}
```

`eventId`, `merchantId`, `journeyId`, `cartId`, `eventType`, `outcome`, `occurredAt`, and `details` are required. Allowed event/outcome pairs are:

| Event types | Required outcome |
|---|---|
| `CART_ADD_SUCCEEDED`, `CART_UPDATE_SUCCEEDED`, `ITEM_REMOVAL_SUCCEEDED`, `PAYMENT_SUCCEEDED` | `SUCCESS` |
| `CART_ADD_FAILED`, `CART_UPDATE_FAILED`, `ITEM_REMOVAL_FAILED`, `PAYMENT_FAILED` | `FAILED` |

`changedItems` contains product IDs and signed quantity deltas. Pure removals are result-only operations and do not call AgentGuard. Reuse the same event ID and payload on retry: identical callbacks return `alreadyReceived: true`; a conflicting reuse is rejected.

Response:

```json
{ "operationId": "op-123", "accepted": true, "alreadyReceived": false, "status": "SUCCEEDED" }
```

## 4. Journey read APIs

### Merchant journey list: `GET /api/v1/journeys?merchantId=STRIDEHUB&status=BLOCKED&search=cart`

Returns a newest-first JSON array containing journey and cart IDs, optional order ID, status/timestamps, latest event/outcome, and latest Guard decision. `status` and `search` are optional; search matches journey, cart, or order ID. The endpoint returns at most 100 journeys. The `merchantId` check is a demo ownership check, not authentication. The merchant UI uses this list to find a journey, then calls the detail endpoint below when a staff member opens it.

The demo currently does not create a separate merchant order record or reliably associate an order ID with every journey. The dashboard therefore labels completed journeys as completed demo checkouts and presents all records under Journeys; a future merchant integration should save the journey ID with its real order.

### Basic event list: `GET /api/journeys/{journeyId}`

Returns `{ "journeyId": "...", "events": [...] }`, or `404` if no events are stored for the journey. Each event includes event ID, journey ID, time, type, source, amount, currency, summary, and structured details.

### Merchant journey: `GET /api/v1/journeys/{journeyId}?merchantId=STRIDEHUB`

Returns `journeyId`, `merchantId`, `cartId`, journey status and timestamps, `latestEventType`, `latestOutcome`, plus:

- `operations`: operation/cart/action/status, decision ID, result event ID, created and resolved times;
- `decisions`: decision and operation IDs, action, Guard outcome, reasons, rule results, evaluation time, and the saved `context` object. Context includes the submitted cart request, detailed rule findings/evidence, policy version, authorization hash, and evaluation fingerprint;
- `timeline`: event ID/type/source/outcome/correlation ID, structured details and time. New demo journeys record authorization registration, merchant operation submission, Guard evaluation, merchant result, and payment result as separate activities. Failed Guard checks and lifecycle timeouts are recorded when they occur.

The five numbered items in the demo UI are only a short flow guide. The saved activity list contains the individual events above; expand an activity to inspect its cart snapshot, decision, rules/reasons, callback, and full saved event/decision data. An AI or dashboard can use this structured API response rather than trying to interpret server logs. The current demo does not save a natural-language conversation transcript because the shopper/agent is simulated and the shopper selects products directly.

The merchant ID comparison is a demo ownership check only; it is not authentication. The endpoint returns stored facts and does not call AgentGuard or PayPal.

### Append simple timeline event: `POST /api/journeys/{journeyId}/events`

Request:

```json
{
  "id": "evt-demo-101",
  "occurredAt": "2026-10-25T09:33:00Z",
  "type": "checkout_started",
  "source": "simulated_demo",
  "amount": 179.00,
  "currency": "USD",
  "details": "Demo checkout started."
}
```

Returns the event with `201 Created`; a duplicate event ID returns `409 Conflict`.

## 5. Local Store Sync simulator API

These routes are enabled by the `storesync-simulator` profile, bind to loopback, and have no Store Sync JWT/signature verifier. The configured demo journey defaults to `demo-shoes-001`.

### Create cart: `POST /merchant-cart`

```json
{
  "items": [{ "variant_id": "shoe-001", "quantity": 1 }],
  "customer": { "name": { "given_name": "Demo", "surname": "Shopper" } },
  "shipping_address": {
    "address_line_1": "1 Main St",
    "admin_area_2": "San Jose",
    "admin_area_1": "CA",
    "postal_code": "95101",
    "country_code": "US"
  },
  "payment_method": { "type": "paypal" }
}
```

Each item needs `variant_id` or legacy `item_id` and quantity at least 1. Catalog price is resolved server-side. Optional customer fields include name, phone, and `email_address`. Address fields use Store Sync snake_case names. Response has cart `id`, status, validation status/issues, items, totals, customer/address fields, payment method, and payment confirmation. If allowed and configured, create also creates a PayPal Orders v2 order and returns its order ID/approval URL in the payment method.

The response uses this shape (values and optional fields vary by validation/order state):

```json
{
  "id": "CART-123",
  "status": "CREATED",
  "validation_status": "VALID",
  "validation_issues": [],
  "items": [
    {
      "variant_id": "shoe-001",
      "quantity": 1,
      "name": "Demo Running Shoe",
      "unit_amount": { "currency_code": "USD", "value": "179.00" },
      "item_total": { "currency_code": "USD", "value": "179.00" }
    }
  ],
  "totals": {
    "subtotal": { "currency_code": "USD", "value": "179.00" },
    "total": { "currency_code": "USD", "value": "179.00" },
    "shipping": { "currency_code": "USD", "value": "0.00" },
    "tax": { "currency_code": "USD", "value": "0.00" }
  },
  "customer": null,
  "shipping_address": null,
  "billing_address": null,
  "payment_method": {
    "type": "paypal",
    "token": "PAYPAL-ORDER-ID",
    "payer_id": null,
    "approval_url": "https://www.sandbox.paypal.com/checkoutnow?token=..."
  },
  "payment_confirmation": null
}
```

### Read/replace cart: `GET /merchant-cart/{cartId}`, `PUT /merchant-cart/{cartId}`

`PUT` replaces the entire item list. An empty `items: []` removes all lines and records a removal result; the pending PayPal order is cleared locally, not cancelled remotely at PayPal.

### Checkout: `POST /merchant-cart/{cartId}/checkout`

```json
{
  "payment_method": {
    "type": "paypal",
    "token": "PAYPAL-ORDER-ID",
    "payer_id": "optional-payer-id"
  }
}
```

The token must match the PayPal order previously associated with the cart. Mandate Lens checks the final cart before PayPal capture. A non-`ALLOW` result prevents capture; success or failure is reported to the journey.

## 6. Outbound AgentGuard service contract

The HTTP client is configured with `agentguard.base-url`; current HTTP calls are restricted to loopback until service authentication is agreed. Mandate Lens sends:

### Register mandate: `POST /api/guard/mandates`

```json
{
  "mandateId": "mandate-demo-shoes-001",
  "journeyId": "demo-shoes-001",
  "userId": "demo-shopper-001",
  "merchantId": "STRIDEHUB",
  "authorization": {
    "source": "SIMULATED_AP2",
    "authorizationReference": "demo-ap2-auth-shoes-001",
    "protocolVersion": "0.2",
    "envelopeHash": "<sha256-of-envelope>",
    "verificationStatus": "SIMULATED"
  },
  "constraints": {
    "maxAmount": 200.00,
    "currency": "USD",
    "allowedCategories": ["RUNNING_SHOES"],
    "shippingCountry": "US",
    "allowedProductIds": []
  },
  "expiresAt": "2026-11-20T08:55:00Z"
}
```

Expected response: `{ "mandateId": "mandate-demo-shoes-001", "status": "REGISTERED" }` (the client also accepts `ACTIVE`).

### Evaluate cart: `POST /api/guard/evaluate/cart`

Request fields: `journeyId`, `mandateId`, `action`, `currentCart`, and `proposedCart`. Each cart contains `cartId`, item list (`productId`, `category`, `quantity`, `unitPrice`), `total`, `currency`, and `shippingCountry`.

### Evaluate checkout: `POST /api/guard/evaluate/checkout`

Request fields: `journeyId`, `mandateId`, and final `cart` using the same cart shape.

Both evaluate calls return `decisionId`, `decision` (`ALLOW`, `REVIEW`, `BLOCK`), `reasons`, `ruleResults`, `policyVersion`, `evaluatedAt`, `contextFingerprint`, and `findings`. Reasons have `code` and `message`; rule results have `ruleId`, `result`, and `message`.

## 7. Common error behavior and security boundary

- Invalid request fields return `400`; unknown journey/operation/cart returns `404` where handled; duplicate/conflicting state or callback use returns `409`.
- AgentGuard or PayPal unavailable in the simulator returns a service/gateway error; do not interpret it as `ALLOW`.
- Only `ALLOW` lets a cart change or PayPal capture proceed. Guard decisions are recorded even when the merchant is told to reject an action.
- These demo routes do not authenticate merchant identity. Store Sync routes are local-only. Do not expose them publicly as production APIs.
- Never send or store card credentials, bearer payment credentials, or raw payment tokens in the mandate envelope or AgentGuard registration payload.

## Related docs and implementation

- [Database schemas](database-schemas.md)
- [Hackathon design and end-to-end flow](mandatelens_design.md)
- Controllers and DTOs: `agentpulse/api`, `mandate/api`, `merchant/integration/storesync` under `src/server/src/main/java/com/emeralds/mandatelens/`.
