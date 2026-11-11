# End-to-end shopping flow

This guide runs one complete purchase through the PacePoint shop or the MCP shop. The demo uses a simulated shopper budget and a local deterministic Guard policy. PayPal Sandbox is optional and uses test accounts; it does not move real money.

## What the flow exercises

```mermaid
sequenceDiagram
    actor Shopper
    participant MCP as AI client / MCP app
    participant Merchant as Merchant API
    participant Guard as Mandate Lens / Guard
    participant PayPal as PayPal Sandbox
    participant Shop as PacePoint UI
    Shopper->>MCP: Set budget and approve item selection
    MCP->>Merchant: Create shopping session and submit cart
    Merchant->>Guard: Check cart; record result
    Shopper->>MCP: Review final Guard decision
    MCP->>Merchant: Checkout preview
    Merchant->>Guard: Check final cart
    alt PayPal Sandbox enabled
        Merchant->>PayPal: Create order after ALLOW
        Shopper->>PayPal: Approve order
        PayPal->>Shop: Return with cartId and order token
        Shop->>Merchant: Validate saved order and capture
        Merchant->>Guard: Record payment result
        Shopper->>MCP: Refresh payment status
        MCP->>Merchant: Read completed cart
    else Sandbox disabled
        Shopper->>MCP: Approve simulated payment
        MCP->>Merchant: Record demo completion
    end
```

The app and API use **PacePoint** as the shop brand and `STRIDEHUB` as the demo merchant ID. Keep that ID when reading its seeded journeys.

## Prerequisites

- Java 17 or newer, Node.js/npm, PostgreSQL, and the repository's Maven wrapper.
- For MCP testing, Python 3.11+ and `uv`.
- An MCP client that renders MCP Apps, such as Cursor or Codex.
- Optional: PayPal Sandbox REST app credentials and a personal Sandbox buyer account.

## Configure the local database

Copy `.env.example` to `.env`, then set the PostgreSQL URL, username, and password for a database named `mandatelens`. Keep `.env` local; it is git-ignored. The Mandate Lens process applies Flyway migrations and seeds demo data. Merchant reads the catalog and cart data from the shared local database.

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```sh
cp .env.example .env
```

## Start the services

Start PostgreSQL first. Then open separate terminals at the repository root and start each process:

```sh
make run-mandatelens
make run-merchant
make run-merchant-ui
make run-agentpulse-ui
```

The first two commands start the APIs; the UI commands are optional unless using the browser checkout return flow. Expected local URLs:

| Component | URL |
| --- | --- |
| Mandate Lens API | `http://localhost:8081` |
| Merchant API | `http://localhost:8080` |
| PacePoint shop | `http://localhost:5173` |
| AgentPulse dashboard | `http://localhost:5174` |
| MCP endpoint | `http://127.0.0.1:8001/mcp` |

Check API readiness and catalog access:

```sh
curl -i http://localhost:8081/api/health
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/products
```

Start MCP in another terminal:

```sh
cd mcp-server
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Connect the AI client to `http://127.0.0.1:8001/mcp` and reload its MCP connection after changing the server.

## Run the purchase in the MCP app

1. Ask the AI client to buy running shoes with a total budget, for example: “Buy one pair of running shoes for up to $200. Show me the selection and wait for my approval.”
2. If the UI asks for a budget, enter it. Review the product, quantity, and total; then click **Approve selection and add to cart**. This starts the demo shopping journey and submits the selected cart for Guard review.
3. Review the saved cart. Click **Check final cart with Guard** and inspect the decision. Payment approval should only be offered after Guard returns `ALLOW`.
4. Continue with one of the payment modes below.

Approving the shopping selection and approving the payment are separate actions. The entered budget is a demo constraint, not cryptographically verified AP2 authorization.

## Option A: PayPal Sandbox checkout

Configure these values in local `.env` before starting Merchant:

```text
PAYPAL_SANDBOX_ENABLED=true
PAYPAL_ORDERS_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_ORDERS_CLIENT_ID=<Sandbox REST app client ID>
PAYPAL_ORDERS_CLIENT_SECRET=<Sandbox REST app secret>
PAYPAL_ORDERS_RETURN_URL=http://localhost:5173/checkout/return
PAYPAL_ORDERS_CANCEL_URL=http://localhost:5173/checkout/cancel
```

Restart Merchant after changing `.env`. Run **Check final cart with Guard** again if the current panel was opened before the change.

1. After an `ALLOW`, click **Open PayPal Sandbox approval** in the MCP app.
2. Sign in with a personal PayPal Sandbox buyer account and approve the order.
3. PayPal returns to the PacePoint UI at `/checkout/return?cartId=...&token=...`. The shop loads the saved cart, checks that the order token matches, and calls Merchant to capture the order.
4. Wait for the PacePoint page to show successful Sandbox checkout. In Merchant logs, look for `PayPal capture completed ... status=COMPLETED` and `Store Sync checkout completed`.
5. Return to the MCP app and click **I approved in PayPal — check payment status**. On success, the MCP panel shows **PAYMENT SUCCESSFUL** and the order ID.

The return URL belongs to the **shop UI**, not an API route. Local Vite serves the React app for `/checkout/return`. For a deployed Render static site, configure a **Rewrite** rule from `/*` to `/index.html` so the client-side return handler loads. Changing the return URL does not change already-created PayPal orders; start a new cart/order after changing it.

Verify the saved cart and journey using the IDs from the MCP result:

```sh
curl -i http://localhost:8080/merchant-cart/<cart-id>
curl -i 'http://localhost:8081/api/v1/journeys/<journey-id>?merchantId=STRIDEHUB'
```

The completed cart should have `status: "COMPLETED"`. The journey should show the cart and final payment outcome.

## Option B: simulated payment

Leave `PAYPAL_SANDBOX_ENABLED=false`. Complete the same selection and final Guard review, then click **Approve & pay (demo)**. The server records a simulated payment result; it does not call PayPal or move money.

## What to check when a step fails

| Symptom | Check |
| --- | --- |
| Product list fails | `MERCHANT_API_BASE_URL` points to Merchant; `GET /api/products` returns 200; inspect Merchant logs. |
| Shopping session fails | Merchant's `MANDATELENS_API_BASE_URL` points to Mandate Lens; inspect both APIs' logs for the same request time. |
| Checkout preview is blocked | Read the Guard decision and reasons; update the cart or use a new valid shopping session. |
| PayPal returns to a 404 | Confirm the return URL uses the shop UI host and its static host rewrites `/*` to `/index.html`. |
| MCP says payment is pending | Confirm the storefront callback ran and Merchant logged a completed capture for the same cart/order. |
| MCP status button produces no logs | Reopen the latest MCP app panel and reconnect the MCP server; a status check should request `GET /merchant-cart/{cartId}`. |

For deployed Render environment variables, per-service URL ownership, and MCP instance guidance, see [the MCP deployment guide](../mcp-server/README.md#paypal-sandbox-from-the-mcp-shop).
