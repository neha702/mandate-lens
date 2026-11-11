# Mandate Lens Merchant MCP demo

This is a separate, local FastMCP project. You start it as a local HTTP MCP server, then an MCP client such as Cursor or Codex connects to it. Footwear lookups call the merchant's live `GET /api/products` endpoint; the MCP App can display those catalog records and product images in a compatible client. Cart changes go through the Merchant API, which asks Mandate Lens/AgentGuard to evaluate the whole proposed cart before saving. The MCP keeps only a small local session-to-cart pointer; Merchant and Mandate Lens hold the durable cart, budget constraint, decisions, and journey history.

The MCP server is a client of Merchant APIs. It does not call AgentGuard directly and it does not own the durable cart or journey data. After it returns the rendered shop MCP App, the client should render the UI and wait for shopper interaction; it should not inspect serialized UI output, run Python/shell code, or make follow-up tool calls to reconstruct the page. Use a client that supports MCP Apps for the interactive UI. A client that only shows JSON/text cannot display the app.

## Start the local MCP server

Open PowerShell in this directory and run:

```powershell
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Start both Spring Boot services first: Merchant at `http://127.0.0.1:8080` and Mandate Lens at `http://127.0.0.1:8081`. Then leave this terminal running. The MCP server listens at `http://127.0.0.1:8001/mcp` and does not open a webpage. Set `MERCHANT_API_BASE_URL` if the API uses another address.

## Connect Cursor to this local server

The repository root has a `.cursor/mcp.json` file that points Cursor to the local endpoint. Start the Merchant API, Mandate Lens, and MCP server, then reload MCP servers in Cursor. For **“Show me running shoes,”** the agent calls `open_shoe_shop` in browse mode and the product cards appear; browsing creates no budget or cart. For a purchase with a stated budget, the shop UI searches the live Merchant catalog and shows the matching affordable selection for approval. For a purchase without a budget, it asks for the budget first; only after the shopper submits it does the UI search Merchant. Requests for **different models** show distinct shoes (one pair per model by default); a request for multiple **pairs** selects a shoe model and uses the requested quantity. The shopper approves the complete selection once before the simulated budget journey starts and Merchant/AgentGuard checks and adds the combined cart. The UI then requires a separate Guard checkout review before showing payment approval. That review displays the actual Mandate Lens decision, reasons, policy version, timestamp, and each rule result. After successful simulated payment, an animated success screen appears; **Go to home page** returns to the full Merchant footwear catalog.

If Cursor does not load the repository MCP configuration, open Cursor's MCP settings and add a server URL: `http://127.0.0.1:8001/mcp`. For Codex CLI/IDE agent, register it separately with `codex mcp add mandate-lens-demo --url http://127.0.0.1:8001/mcp`; Cursor's `.cursor/mcp.json` is not Codex configuration. Detailed client setup and example prompts are in [the Windows local setup guide](../docs/windows-local-setup.md#test-with-an-ai-agent-through-mcp).

## PayPal Sandbox from the MCP shop

The MCP shop supports the real PayPal Sandbox approval flow when the **Merchant API** has `PAYPAL_SANDBOX_ENABLED=true` and valid Sandbox REST app credentials. Merchant runs the final Guard review first; only an `ALLOW` creates a PayPal order and displays **Open PayPal Sandbox approval**. The buyer approves in a Sandbox buyer account. PayPal returns the browser to the Merchant storefront, which verifies the returned order token and captures the order through Merchant. Return to the MCP panel and click **I approved in PayPal — check payment status** to finish the app flow. Sandbox uses test accounts and does not move real funds.

For Render, make sure the Merchant API service starts with `SPRING_PROFILES_ACTIVE=merchant-server,storesync-simulator` and `SERVER_ADDRESS=0.0.0.0`, then configure these on the **Merchant API service**:

```text
PAYPAL_SANDBOX_ENABLED=true
PAYPAL_ORDERS_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_ORDERS_CLIENT_ID=<Sandbox REST app client ID>
PAYPAL_ORDERS_CLIENT_SECRET=<Sandbox REST app secret>
PAYPAL_ORDERS_RETURN_URL=https://<storefront-host>/checkout/return
PAYPAL_ORDERS_CANCEL_URL=https://<storefront-host>/checkout/cancel
STOREFRONT_ORIGIN=https://<storefront-host>
```

Set the storefront's `VITE_API_URL` to the Merchant API base URL and rebuild/redeploy the frontend. The service-specific URLs are:

| Render service | Required URL settings |
| --- | --- |
| Merchant API | `MANDATELENS_API_BASE_URL=https://<mandate-lens-api-host>`; PayPal settings above; `STOREFRONT_ORIGIN=https://<storefront-host>` |
| Mandate Lens API | `MERCHANT_API_BASE_URL=https://<merchant-api-host>` |
| MCP server | `MERCHANT_API_BASE_URL=https://<merchant-api-host>` |
| Storefront | Build-time `VITE_API_URL=https://<merchant-api-host>` |

Do not leave the localhost return/cancel URL defaults on Render. After changing environment variables, redeploy the affected services. Use only Sandbox credentials; never paste secrets into chat or commit them.

Keep the MCP service at one instance while panelists are testing: its active MCP-session-to-cart pointer is held in process memory. A restart or scaling to multiple instances during a purchase can interrupt the panel's ability to refresh that payment, even though the Merchant cart and PayPal order remain stored.

## Test with MCP Inspector instead of Cursor

This is useful when you want to call each MCP tool yourself without relying on an AI agent. Keep the MCP server terminal running. Open a second PowerShell window and run:

```powershell
npx -y @modelcontextprotocol/inspector --server-url http://127.0.0.1:8001/mcp --transport http
```

Inspector opens its own local browser page intentionally. In the Inspector, connect to the server if it did not connect automatically, open **Tools**, and call `test_mcp_connection` to verify callable-tool access. The `start_shopping_session` function is intentionally private to the shop UI, so Inspector cannot use it to bypass the shopper's budget approval. Use Cursor or another MCP Apps client for the complete interactive purchase flow.

Inspector is a manual MCP client. It displays callable tools and their arguments but does not render the private session-approval form, so use it for tool discovery and the safe diagnostic only. The full budget, cart, and checkout UI flow requires an MCP Apps-capable client.

For a different-model purchase prompt, say: **“Buy 3 different running shoes with a total budget of $1000. Show me the three distinct models, one pair of each, with the combined total and wait for my approval before creating the journey or changing the cart.”** For a quantity purchase, say: **“Buy 2 pairs of running shoes…”**; that means one model with quantity two. If there is no budget, the UI asks for it before searching Merchant. With a budget already stated, the UI searches immediately and displays the proposed selection for approval. After Guard allows the selection, click **Check final cart with Guard** and inspect the Mandate Lens response shown in the UI. With PayPal Sandbox enabled, the ALLOW result provides an **Open PayPal Sandbox approval** button; PayPal returns the buyer to the Merchant storefront, which captures the approved order. Return to the MCP panel and check payment status to finish the Pulse journey. With Sandbox disabled, the MCP panel instead offers the simulated **Approve & pay (demo)** action. Successful checkout ends the MCP shopping session and shows the full footwear catalog.

## Important: do not use the browser-preview command

Do not run `fastmcp dev apps server.py` for the Cursor demo. That is a UI development preview and intentionally opens a browser. For the normal demo, use only `uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001` above. It starts the MCP endpoint without opening a webpage.

## Checkout diagnostic logs

The MCP process and Merchant process now log the checkout path without printing request bodies, credentials, or authorization data. Keep both PowerShell windows open while reproducing the issue:

- **MCP window:** look for `Checkout preview started`, then `Merchant HTTP request started ... checkout-preview`, followed by either `Checkout preview response` or `Checkout preview request failed`.
- **Merchant window (port 8080):** look for `HTTP checkout-preview received`, `Store Sync checkout preview started`, then `Mandate Lens decision request started` and `Mandate Lens decision response received`. A rejected request logs an HTTP status and reason.
- **Mandate Lens window (port 8081):** look for `AgentGuard check started: action=CHECKOUT` and `AgentGuard check completed`. If those lines are absent, checkout did not reach Guard.

`INFO` is detailed enough for normal diagnosis. To enable additional Python runtime messages, set `$env:MCP_LOG_LEVEL = "DEBUG"` in the MCP PowerShell window before starting it. After reproducing, share the log lines from the click time in all three windows; the journey ID, cart ID, and operation ID help match the same request across services.

## Demo MCP tools

Three entry points are visible to the AI model: `open_shoe_shop` (the visual shop UI), `test_mcp_connection` (a safe diagnostic), and `get_purchase_journey_summary` (a read-only journey lookup). Shopping actions are private to the app UI using `@shop.tool(model=False)`, so the AI cannot skip the UI and call them as chat tools. The UI calls these helpers when the shopper interacts with its forms and buttons:

- `get_footwear_products`: private catalog lookup; the AI should open the shop UI instead of returning a raw product list in chat.
- `prepare_purchase_item`: private UI action that searches Merchant after the budget is submitted and selects the requested distinct models/quantities for review.
- `start_shopping_session`: private UI action that starts the journey only after the shopper approves the proposed selection.
- `add_item_to_demo_cart`: private cart action; Merchant and Guard evaluate the complete proposed cart.
- `reset_demo_cart`: private cart reset action.
- `preview_demo_checkout`: private action called by the **Check final cart with Guard** button.
- `approve_demo_checkout` / `decline_demo_checkout`: private actions used by the simulated payment mode.
- `refresh_paypal_checkout_status`: private action that reads the saved Merchant cart after the PayPal approval return flow captures the order.
- `get_purchase_journey_summary`: visible to the AI; reads a saved journey from Mandate Lens and returns the deterministic explanation plus the recorded operations, Guard decisions, and event timeline. It cannot change a cart or payment.

The shop UI and private cart helpers use the `app` visibility; `open_shoe_shop`, `test_mcp_connection`, and `get_purchase_journey_summary` are model-visible. The journey lookup calls `http://127.0.0.1:8081` by default; set `MANDATE_LENS_API_BASE_URL` if Mandate Lens uses another address.

## Merchant API connection

The product API is `GET /api/products`. The MCP server filters its results to footwear categories and uses the merchant's IDs, descriptions, prices, availability, and images. The merchant currently comes from `http://127.0.0.1:8080` by default. Once the shopper approves the selected shoes in the UI, Merchant asks Mandate Lens to create a journey and registers the simulated budget constraint; Guard checks the combined proposed cart before Merchant saves it. This is not verified AP2 evidence. If an open cart ID is supplied, Merchant preserves the cart and re-links it to the new journey. Each cart change is Guard-checked and reported to Pulse. **Check final cart with Guard** runs the checkout-specific policy check and displays the actual Mandate Lens response; payment approval remains disabled until Guard returns ALLOW. With PayPal Sandbox enabled, Guard ALLOW creates a PayPal order and the panel opens PayPal's buyer approval page. PayPal returns to the deployed Merchant storefront, which captures the order after validating the returned order token and reusing the pending Guard approval. The MCP panel then reads the completed cart to finish the journey. Configure `PAYPAL_ORDERS_RETURN_URL` and `PAYPAL_ORDERS_CANCEL_URL` to the public storefront's `/checkout/return` and `/checkout/cancel` routes; do not leave the local `localhost:5173` defaults on Render. Sandbox transactions use test accounts and do not move real funds. With Sandbox disabled, the MCP payment button remains a local simulation. Starting a new shopping session does not delete older carts or journeys.

If the merchant API is unavailable, the MCP tool returns an error instead of silently showing stale hardcoded product data. No PayPal credentials are used by this product lookup.
