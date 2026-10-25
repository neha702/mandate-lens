# Local end-to-end testing

This guide walks through the local Mandate Lens API and, optionally, a full Store Sync checkout against PayPal Sandbox. The repository currently has no automated end-to-end test classes; these are manual HTTP checks. Maven's `test` goal runs the unit tests when present.

## 1. Start PostgreSQL and the API

Use Java 17+, PostgreSQL, and the Maven wrapper. Create a local database named `mandatelens` and make sure the connection matches the defaults below, or set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` in the API terminal. Flyway applies the schema and the application seeds the demo journey, mandate, and catalog on startup.

From the repository root, start the API with the deterministic local AgentGuard implementation:

```sh
SPRING_PROFILES_ACTIVE=agentguard-deterministic ./mvnw -pl src/server spring-boot:run
```

In a second terminal, check that it is ready and inspect the seeded data:

```sh
curl -i http://localhost:8080/api/health
curl -s http://localhost:8080/api/products
curl -s 'http://localhost:8080/api/v1/journeys/demo-shoes-001?merchantId=STRIDEHUB'
```

The demo mandate is simulated and allows up to USD 200 for `RUNNING_SHOES` shipped to `US`. Product `shoe-001` costs USD 179; `shoe-003` costs USD 189.

## 2. Exercise the merchant operation check and result APIs

Submit an allowed add-item check. The API response contains an `operationId`; use that exact value in the result callback below. The idempotency key must be unique for each new check.

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

Read the journey URL from step 1 again. It should now include the decision, operation status, and result event. To check a block, repeat the check with a new cart ID and idempotency key and a proposed total above USD 200.

## 3. Optional: run Store Sync through PayPal Sandbox

This path calls PayPal Orders v2 to create and capture an order. It requires PayPal Sandbox REST app credentials and a Sandbox buyer account. Create an ignored repository-root `.env` file with:

```properties
PAYPAL_ORDERS_CLIENT_ID=your-sandbox-client-id
PAYPAL_ORDERS_CLIENT_SECRET=your-sandbox-client-secret
```

Start another API instance (stop the API from step 1 first) with the local permissive AgentGuard stub and Store Sync simulator enabled:

```sh
SPRING_PROFILES_ACTIVE=storesync-simulator,storesync-sandbox-test ./mvnw -pl src/server spring-boot:run
```

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

Open the approval URL, approve with a Sandbox buyer, and return to the local app. Then capture the approved order (replace the placeholders with the cart ID and returned PayPal order ID):

```sh
curl -s -X POST 'http://localhost:8080/merchant-cart/<cartId>/checkout' \
  -H 'Content-Type: application/json' \
  -d '{"payment_method":{"type":"paypal","token":"<paypalOrderId>"}}'
```

The response should have status `COMPLETED` and a payment confirmation. The `storesync-sandbox-test` profile always returns `ALLOW` from its local Guard stub; use the `agentguard-deterministic` profile in step 2 to exercise local policy decisions. Do not expose the Store Sync simulator outside localhost; its local profile does not verify PayPal JWTs.

## Troubleshooting

- **Database connection refused:** start PostgreSQL and check the three `DATABASE_*` settings.
- **No active mandate / journey:** confirm the app started successfully and use `demo-shoes-001` with merchant `STRIDEHUB`.
- **PayPal is not configured:** add valid Sandbox credentials to the root `.env` and restart the application.
- **PayPal order or capture fails:** make sure you approved the order using a Sandbox buyer and that the API is using Sandbox credentials.
- **Port 8080 is busy:** stop the other API process before starting the alternate profile.

For request and response shapes, see [API contracts](api-contracts.md#5-local-store-sync-simulator-api).
