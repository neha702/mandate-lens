# Local end-to-end testing

This guide walks through the local Mandate Lens API and, optionally, a full Store Sync checkout against PayPal Sandbox. The repository currently has no automated end-to-end test classes; these are manual HTTP checks. Maven's `test` goal runs the unit tests when present.

## 1. Start PostgreSQL and the API

Use Java 17+ and PostgreSQL. Create a local database named `mandatelens`, then put its connection values in the ignored repository-root `.env` file (`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`). Set `SPRING_PROFILES_ACTIVE=storesync-simulator,agentguard-deterministic` there to run the Store Sync simulator with local policy enforcement. Flyway applies the schema and the application seeds the demo journey, mandate, and catalog on startup. Spring Boot imports this file automatically when the app starts from the repository root.

From the repository root, start the API. On Windows PowerShell, use the script so `.env` values are loaded into the backend process:

```powershell
.\run-server.ps1
```

On macOS/Linux, `make run-server` sources `.env` through `local-env.sh` and starts the Maven wrapper. To exercise only the merchant operation API, set `SPRING_PROFILES_ACTIVE=agentguard-deterministic` in `.env`; include `storesync-simulator` when you also want the Store Sync routes. Restart the server after changing profiles.

In a second terminal, check that it is ready and inspect the seeded data:

```sh
curl -i http://localhost:8080/api/health
curl -s http://localhost:8080/api/products
curl -s 'http://localhost:8080/api/v1/journeys/demo-shoes-001?merchantId=STRIDEHUB'
```

The demo mandate is simulated and allows up to USD 600 by default for `RUNNING_SHOES` shipped to `US`. Set `AGENTGUARD_DEMO_MAX_AMOUNT` in the root `.env` to change the local demo limit. The expanded catalog includes lower-priced shoes for multi-item cart examples.

## 2. Exercise the merchant operation check and result APIs

Submit an allowed add-item check. The API response contains an `operationId`; use that exact value in the result callback below. The idempotency key must be unique for each new check.

Watch the API terminal for `HTTP request completed`, `AgentGuard check started`, and `AgentGuard check completed` entries. They show the request status/duration and the selected Guard client, proposed cart total, mandate limit, final decision, and rule results. Each HTTP response includes an `X-Request-Id` that matches the request log. Request/response bodies are not logged.

```sh
curl -s -X POST http://localhost:8080/api/v1/merchant-operations/check \
  -H 'Content-Type: application/json' \
  -d '{
    "merchantId":"STRIDEHUB",
    "journeyId":"demo-shoes-001",
    "cartId":"local-e2e-cart-001",
    "action":"ADD_ITEM",
    "idempotencyKey":"local-e2e-add-001",
    "currentCart":{"currency":"USD","items":[],"total":0},
    "proposedCart":{"currency":"USD","items":[{"productId":"shoe-001","category":"RUNNING_SHOES","quantity":1,"unitPrice":179}],"total":179},
    "shippingCountry":"US"
  }'
```

After you copy the returned `operationId`, report the merchant outcome (replace `<operationId>`):

```sh
curl -i -X POST 'http://localhost:8080/api/v1/merchant-operations/<operationId>/result' \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId":"local-e2e-event-001",
    "merchantId":"STRIDEHUB",
    "journeyId":"demo-shoes-001",
    "cartId":"local-e2e-cart-001",
    "eventType":"CART_ADD_SUCCEEDED",
    "outcome":"SUCCESS",
    "occurredAt":"2026-10-25T12:00:00Z",
    "merchantReference":"local-e2e-cart-001",
    "details":{"total":179,"currency":"USD","changedItems":[{"productId":"shoe-001","quantityDelta":1}]}
  }'
```

Read the journey URL from step 1 again. It should now include the decision, operation status, and result event. To check a block, repeat the check with a new cart ID and idempotency key and a proposed total above the configured demo limit.

## 3. Choose simulated checkout or PayPal Sandbox

The `PAYPAL_SANDBOX_ENABLED` setting in the ignored repository-root `.env` selects the payment mode:

- `PAYPAL_SANDBOX_ENABLED=false` (default): no request is sent to PayPal. Checkout is simulated locally, so you can demonstrate the whole merchant and Guard flow without PayPal credentials.
- `PAYPAL_SANDBOX_ENABLED=true`: the backend creates and captures actual PayPal Sandbox orders. Set the Sandbox REST app Client ID and Secret as well, and approve with a personal Sandbox buyer account. These are test transactions; no real funds move.

Restart the backend after changing the flag. Start a fresh cart in the selected mode. A cart created in one mode cannot be checked out in the other; clear it and start again after switching.

## 4. Optional: run Store Sync through PayPal Sandbox

This path calls PayPal Orders v2 to create and capture an order. It requires PayPal Sandbox REST app credentials and a Sandbox buyer account. Put credentials in the ignored repository-root `.env` file:

In the PayPal Developer Dashboard, choose Sandbox mode, copy the REST app Client ID and Secret from **Apps & Credentials**, and use a **personal Sandbox account** to approve the payment in the browser. The app secret stays on the Spring Boot server in `.env`; the React page never receives it. PayPal Sandbox creates test transactions between fictional buyer/seller accounts, so no real funds move. See [PayPal's Sandbox guide](https://developer.paypal.com/sandbox-testing/overview/) and [Sandbox account guide](https://developer.paypal.com/sandbox-testing/accounts).

The PacePoint storefront now exercises these same Store Sync endpoints: it creates a cart when the first product is added, reads a saved cart on reload, replaces the cart as quantities or shipping details change, and calls checkout after the buyer returns from PayPal approval. For direct API checks, continue with the HTTP examples below.

```properties
PAYPAL_ORDERS_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_SANDBOX_ENABLED=true
PAYPAL_ORDERS_CLIENT_ID=your-sandbox-client-id
PAYPAL_ORDERS_CLIENT_SECRET=your-sandbox-client-secret
PAYPAL_ORDERS_RETURN_URL=http://localhost:5173/checkout/return
PAYPAL_ORDERS_CANCEL_URL=http://localhost:5173/checkout/cancel
```

For the end-to-end checkout with policy checks enabled, set this profile in `.env`:

```sh
SPRING_PROFILES_ACTIVE=storesync-simulator,agentguard-deterministic
```

Stop the currently running server, then run it again so it loads the Sandbox flag and credentials from `.env`. Start the merchant website from a second terminal in PowerShell with `cd storefront` and `npm run dev`. Open the Vite address it prints, add a shoe, check the cart, then continue to PayPal Sandbox. Sign in with a **personal (buyer) Sandbox account**. After approval, PayPal returns to the local storefront, which asks the backend to run the final Guard check and capture the test order. API examples below can also be run directly with `curl`.

Create a cart. The `shipping_address.country_code` is needed for Guard evaluation; the cart is priced from the local catalog. Copy the response's `id`, `payment_method.token` (PayPal order ID), and `payment_method.approval_url`:

```sh
curl -s -X POST http://localhost:8080/merchant-cart \
  -H 'Content-Type: application/json' \
  -d '{
    "items":[{"variant_id":"shoe-001","quantity":1}],
    "shipping_address":{"address_line_1":"1 Main St","admin_area_2":"San Jose","admin_area_1":"CA","postal_code":"95131","country_code":"US"},
    "payment_method":{"type":"paypal"}
  }'
```

To exercise the flow by API instead of the merchant UI, open the returned approval URL and approve with a Sandbox buyer. Then capture the approved order (replace the placeholders with the cart ID and returned PayPal order ID):

```sh
curl -s -X POST 'http://localhost:8080/merchant-cart/<cartId>/checkout' \
  -H 'Content-Type: application/json' \
  -d '{"payment_method":{"type":"paypal","token":"<paypalOrderId>"}}'
```

The response should have status `COMPLETED` and a payment confirmation. Do not expose the Store Sync simulator outside localhost; its local profile does not verify PayPal JWTs.

For PayPal protocol-only testing, you can switch the profile in `.env` to the permissive Guard stub:

```sh
SPRING_PROFILES_ACTIVE=storesync-simulator,storesync-sandbox-test
```

Restart with `make run-server`. This stub always returns `ALLOW`; use `storesync-simulator,agentguard-deterministic` to enforce the configured demo mandate limit. Under deterministic mode, a cart total above that limit should be rejected with HTTP `409` before its PayPal order is created or updated.

## Troubleshooting

- **Database connection refused:** start PostgreSQL and check the three `DATABASE_*` settings.
- **No active mandate / journey:** confirm the app started successfully and use `demo-shoes-001` with merchant `STRIDEHUB`.
- **PayPal is not configured:** for local simulated checkout, set `PAYPAL_SANDBOX_ENABLED=false`; for PayPal Sandbox, set it to `true`, add valid Sandbox credentials to the root `.env`, and restart the application.
- **PayPal order or capture fails:** make sure you approved the order using a Sandbox buyer and that the API is using Sandbox credentials.
- **Port 8080 is busy:** stop the other API process before starting the alternate profile.

For request and response shapes, see [API contracts](api-contracts.md#5-local-store-sync-simulator-api).
