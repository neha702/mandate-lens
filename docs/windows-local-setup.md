# Local demo setup on Windows (PowerShell)

The demo now runs as separate local pieces:

1. **Mandate Lens API** — AgentPulse journeys, AgentGuard checks/orchestration, mandate records; port `8081`.
2. **Merchant API** — PacePoint catalog, cart, and payment adapter; port `8080`.
3. **Merchant shop UI** — port `5173`.
4. **Mandate Lens Pulse dashboard** — a separate UI owned by Mandate Lens; port `5174`.
5. **Footwear MCP server** — optional local MCP endpoint; port `8001`.
6. **MCP Inspector** — optional manual MCP client for calling tools without an AI agent. Its web UI runs locally on a separate port.

For this first split, both APIs use the existing local PostgreSQL database. Mandate Lens runs the migrations; Merchant does not. PayPal Sandbox stays disabled (`PAYPAL_SANDBOX_ENABLED=false`), so checkout is simulated and no money moves.

## One-time setup

Install Java 17+, Node.js/npm, PostgreSQL, Python, and `uv`. Create a PostgreSQL database named `mandatelens` if it does not already exist. From the repository root, create the private `.env` file from the example and set your local PostgreSQL credentials:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Keep `.env` private; it is ignored by Git. For the local demo leave PayPal Sandbox disabled and leave PayPal credentials blank.

## Start the two backend services

Use two PowerShell windows from the repository root. `run-server.ps1` reads `.env` and loads its values into that process; PowerShell/Maven do not load `.env` automatically.

**Window 1 — start Mandate Lens first** so it can create/update the database tables:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens"
.\run-server.ps1 -Service MandateLens
```

Mandate Lens API: `http://localhost:8081`.

**Window 2 — start Merchant:**

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens"
.\run-server.ps1 -Service Merchant
```

Merchant API: `http://localhost:8080`. It calls Mandate Lens over HTTP for Guard decisions and reports the merchant's actual cart/payment outcome back to Mandate Lens. Keep both windows open.

Check both services in a third PowerShell window:

```powershell
curl.exe http://localhost:8081/api/health
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/products
```

## Start the two UIs

**Window 3 — merchant shop:**

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\storefront"
npm ci
npm run dev
```

Open `http://localhost:5173`. The shop UI calls the Merchant API on port 8080.

**Window 4 — Pulse dashboard:**

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\mandate-lens-ui"
npm ci
npm run dev
```

Open `http://localhost:5174`. This dashboard reads journey history from the Mandate Lens API on port 8081. The merchant shop header links to it.

Run `npm ci` once per UI, then only again if its dependencies change.

## Optional: start the MCP server

Make sure Merchant is running because footwear lookup calls Merchant's `GET /api/products`. In a fifth PowerShell window:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\mcp-server"
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Keep this window open. The MCP endpoint is `http://127.0.0.1:8001/mcp`. The `uv run fastmcp ...` command starts only the server and does not open a web page.

## Test MCP tools manually with Inspector (no AI agent)

Use this if you want to choose each MCP tool and fill in its arguments yourself. Node.js 22.19 or newer is required by the current MCP Inspector. Check your version in a new PowerShell window:

```powershell
node --version
```

In another PowerShell window, start Inspector and point it at the running MCP server:

```powershell
npx -y @modelcontextprotocol/inspector --server-url http://127.0.0.1:8001/mcp --transport http
```

Inspector intentionally opens a local browser page. This is the Inspector interface; it does not mean the MCP server itself opened a page. In Inspector, connect if needed, open **Tools**, and manually call:

1. Call `test_mcp_connection` to confirm the Inspector can reach the MCP server. This changes no shopping data.
2. Use `get_footwear_products` with `query` = `running` to check that the live catalog is reachable and see product IDs.
3. The full buy flow includes a shopper-facing budget approval form inside the MCP shop UI. `start_shopping_session` is private to that UI, so Inspector cannot start a purchase session or bypass that approval. Use Cursor (or another MCP Apps client) for the full flow described below.

To see the saved journey, optionally start the Pulse dashboard (Window 4), open `http://localhost:5174`, select merchant `STRIDEHUB`, and open the `journeyId` from step 1. For a block example, start a new session with a small budget and try adding an item priced above it. Guard should block it and the cart should remain unchanged.

This Inspector walkthrough is a manual tool test, so there is no natural-language AI agent deciding which tool to call. The simulated budget is not a real AP2 authorization, and this MCP checkout makes no PayPal Sandbox or real-money transaction. Use the Merchant storefront UI for the separate PayPal Sandbox checkout path.

## Test with an AI agent through MCP

The MCP server is a tool provider. Cursor or Codex acts as the MCP client: the AI reads your request, chooses a relevant MCP tool, and shows you its result. Keep PostgreSQL, Mandate Lens, Merchant, and the MCP server running as described above.

### Option A — Cursor Agent

The repository's `.cursor/mcp.json` points Cursor to `http://127.0.0.1:8001/mcp`.

1. Open the repository folder in Cursor.
2. Open **Cursor Settings → Tools & MCP** (wording may vary by Cursor version). Reload or refresh MCP servers.
3. Confirm `mandate-lens-demo` is listed and connected. Enable its tools in the tools list if they are toggled off.
4. Open an **Agent** chat. Keep tool approvals enabled so Cursor asks before each action.
5. To check that tool calls work before shopping, ask: **“Call `test_mcp_connection` on the `mandate-lens-demo` server now. Return the exact result.”** A successful call returns `ok: true`, a confirmation message, and a UTC timestamp; it does not access or change the cart.
6. To browse only, ask:

   > Show me running shoes from the Merchant catalog. I only want to browse; do not start a shopping session.

   This opens the catalog for browsing only. It does not create a journey, show budget authorization, or change a cart.
7. For a purchase request without a budget, ask the agent to open the purchase UI and collect the budget before searching, for example:

   > Buy two pairs of running shoes. I have not set a budget. Open the purchase UI and ask me for my total budget first. Only after I submit it, search Merchant, show one matching shoe with quantity 2 and its total, and wait for my approval before starting the journey or adding it.

   To ask for separate models instead, use a prompt such as:

   > Buy 3 different running shoes with a total budget of $1000. Show me three distinct models, one pair of each, with the combined total. Wait for my approval before starting the journey or changing the cart.

   “Different shoes/models” means distinct products, one pair each unless you specify otherwise. “Three pairs” means quantity three of one selected model. After the cart is built, **Check final cart with Guard** displays the Mandate Lens decision, reasons, policy version, evaluation time, and rule results. Payment approval is shown only when the decision is ALLOW.

   The no-budget path must not call the product API before you submit the budget. After you submit it, the UI searches the live Merchant catalog and selects the least expensive matching in-stock item that fits your total budget. If you already gave a budget in chat, the UI can search immediately and show the selected item for approval.
8. Review the selected shoe, quantity, and total. Click **Approve this shoe and add to cart**. This creates the demo journey and asks Merchant/Guard to check and add the selected item. To reject, click **Decline and return to the shop**; no journey or cart change is created.
9. After Guard allows the item, the UI shows the Merchant cart and **Check final cart with Guard**. This runs the final checkout check; if Guard allows it, separately approve or decline the simulated payment. Successful payment completes the Pulse journey, ends the MCP shopping session, and returns the UI to the full Merchant footwear catalog. Adding an item alone does not finish checkout; the journey remains active until payment succeeds or the mandate expires.

10. Ask it to run the final Guard check without paying:

   > Check the final cart with Guard and explain the result. Do not approve or pay.

11. Only if you want to finish the simulated purchase, explicitly ask:

   > I approve this simulated demo checkout. Call the approve demo checkout tool.

   To stop instead, ask it to call the decline tool. A declined checkout records the event and makes no payment.

Cursor reads project-specific MCP settings from `.cursor/mcp.json` and lets you enable/disable configured tools. See [Cursor's MCP documentation](https://docs.cursor.com/context/model-context-protocol).

### Option B — Codex CLI / IDE agent

Codex does **not** read Cursor's `.cursor/mcp.json`. Configure the same local HTTP server in Codex separately. In PowerShell on the same computer, run:

```powershell
codex mcp add mandate-lens-demo --url http://127.0.0.1:8001/mcp
codex mcp list
```

Confirm `mandate-lens-demo` is listed, then open/reload the Codex agent session and check that its MCP tools are available. Use the same prompts as the Cursor steps above, asking the agent to use `mandate-lens-demo`. The Codex CLI and IDE extension share their MCP configuration. See [OpenAI's Codex MCP setup](https://developers.openai.com/learn/docs-mcp).

This address uses `127.0.0.1`, so the agent must run on the same computer as the MCP server. A remote/cloud agent cannot reach your PC's localhost address without a separate secure tunnel. This guide keeps everything local.

In either client, the budget is a simulated demo constraint, not a real AP2 authorization. Checkout payment is simulated; PayPal Sandbox remains disabled.

## Recommended startup order and ports

Start PostgreSQL first, then Mandate Lens (`8081`), Merchant (`8080`), and MCP (`8001`) for an Inspector or Cursor test. The storefront (`5173`) and Pulse dashboard (`5174`) are optional for tool testing; start Pulse if you want to view saved journey history. Stop each process with **Ctrl+C** in its own PowerShell window.

## Troubleshooting

- **Database password error:** verify `DATABASE_PASSWORD` in root `.env`, then stop and restart both backend processes with `run-server.ps1`.
- **Port already in use:** stop the other process already using `8080`, `8081`, `5173`, `5174`, or `8001`.
- **Merchant shop cannot load products:** check Merchant health at `http://localhost:8080/api/health` and catalog at `http://localhost:8080/api/products`.
- **Merchant says Mandate Lens is unavailable:** check `http://localhost:8081/api/health` and confirm it started before Merchant.
- **Dashboard cannot load journeys:** check Mandate Lens health at port `8081` and select a merchant ID that has journey history (demo default: `STRIDEHUB`).
- **MCP client cannot connect or shows no tools:** confirm the MCP terminal is still running at `http://127.0.0.1:8001/mcp`. In Inspector, check the transport is HTTP; in Cursor, reload the server; in Codex, run `codex mcp list` and confirm the URL is correct.
