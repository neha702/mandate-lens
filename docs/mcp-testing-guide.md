# Baby-step guide: test the Merchant MCP demo

This guide walks through a successful shopping demo with Cursor or VS Code, then a deliberate AgentGuard block using MCP Inspector. The default flow is local and uses demo data. PayPal Sandbox is an optional checkout mode that uses test accounts and does not move real funds; the shopper budget is still a simulated constraint, not a verified AP2 authorization.

## 1. What you will run

| Part | Purpose | Address |
|---|---|---|
| PostgreSQL | Saves Merchant carts and Mandate Lens journeys | `localhost:5432` |
| Mandate Lens API | Journey, simulated mandate, AgentGuard decisions | `http://127.0.0.1:8081` |
| Merchant API | Product catalog, cart, checkout | `http://127.0.0.1:8080` |
| MCP server | Tools and interactive shoe shop UI for an AI client | `http://127.0.0.1:8001/mcp` |
| Pulse dashboard | Reads saved journey and Guard history | `http://localhost:5174` |
| Storefront | Separate Merchant website; optional for this MCP exercise | `http://localhost:5173` |
| MCP Inspector | Manual MCP tool calling; browser UI on a separate local port | printed by Inspector |

Keep each server in its own PowerShell window. Do not close those windows during the walkthrough.

## 2. Prerequisites

Install and verify these first:

1. **Git** — to get the project files.
2. **Java 17 or newer** — check with `java -version`.
3. **PostgreSQL** — the database service must be installed and running. pgAdmin is optional; it is only a graphical way to manage PostgreSQL.
4. **Node.js 22.19 or newer** — needed for the current MCP Inspector. Check with `node --version` and `npm --version`.
5. **uv** — Python project/package runner. Check with `uv --version`.
6. **Cursor or VS Code** for the AI-agent UI demonstration. Cursor has the simplest project MCP setup in this repository. VS Code MCP support depends on the installed agent extension.

Open PowerShell in the repository root:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens"
```

## 3. Prepare the database and `.env`

1. Start the PostgreSQL Windows service from the Start menu's **Services** app or the PostgreSQL installer tools.
2. Ensure a database named `mandatelens` exists. If you already created it during the earlier setup, keep using it.
3. In the repository root, make a private `.env` from the example if you do not already have one:

```powershell
Copy-Item .env.example .env
notepad .env
```

4. Set your local PostgreSQL connection values in `.env`, for example:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5432/mandatelens
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=YOUR_LOCAL_POSTGRES_PASSWORD
```

Replace the sample password with the one configured on your computer. Do not paste credentials into chat or commit `.env`; it is ignored by Git. Keep the PayPal sandbox disabled for this MCP guide.

## 4. Start the backend services

### Window 1 — Mandate Lens

From the repository root, run:

```powershell
.\run-server.ps1 -Service MandateLens
```

Wait for Spring Boot to say it started on port `8081`. Leave this window open.

### Window 2 — Merchant

Open a second PowerShell window, go to the repository root, and run:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens"
.\run-server.ps1 -Service Merchant
```

Wait for Spring Boot to say it started on port `8080`. Leave this window open. `run-server.ps1` loads the ignored root `.env` into the process; running Maven directly does not load it for you.

### Check the APIs

In a third PowerShell window, run:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/products | Select-Object -First 1
```

You should see a product record. If this fails, check that Merchant is running and that its window has no startup/database error. The MCP catalog comes from this Merchant API.

## 5. Start the MCP server

Open another PowerShell window:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\mcp-server"
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Wait for the server to start. This command runs the MCP endpoint and **does not open a browser**. Keep the window open. The URL clients connect to is:

```text
http://127.0.0.1:8001/mcp
```

If your Merchant or Mandate Lens APIs use different ports, set `MERCHANT_API_BASE_URL` or `MANDATE_LENS_API_BASE_URL` in the MCP process environment before starting it.

## 6. Connect an AI agent and show the shop UI

### Cursor

1. Open the repository folder in Cursor.
2. The project already has `.cursor/mcp.json` configured for `http://127.0.0.1:8001/mcp`.
3. Open **Cursor Settings → Tools & MCP** (the label may differ by Cursor version).
4. Reload/refresh the MCP server list. Enable **mandate-lens** if it has an enable toggle.
5. Confirm it reports connected and has the Mandate Lens demo tools available.
6. Open Cursor Chat/Agent mode and select a model that can use tools.
7. Ask: **“Show me the running shoes from the shop.”**
8. The agent should call the shop UI tool. A shoe catalog UI should appear in chat. This is browsing only; it should not ask for a budget or create a journey.

### VS Code

MCP client setup depends on the AI extension. If that extension supports MCP servers, add the HTTP server URL `http://127.0.0.1:8001/mcp` in that extension's MCP settings, enable/reload it, and check its tools list. The repository's `.cursor/mcp.json` is Cursor-specific and may not be read by VS Code. Then use the same prompts below in that extension's agent/chat view. If the extension cannot render MCP Apps, it may call tools but not show the interactive shop UI; use Cursor or Inspector for the visual interaction.

### Successful purchase demo

Enter a casual prompt with a budget, for example:

> Buy me 2 pairs of running shoes. My budget is $400.

Expected flow:

1. The agent opens the interactive shop with a purchase selection, not a plain text claim that it bought something.
2. The UI shows the selected shoe, quantity, unit price, line total, and budget.
3. Review the exact selection. Click **Approve selection and add to cart** only if it is what you want. This is the demo shopper approval before the cart/journey begins.
4. The UI shows the Merchant cart. Click **Check final cart with Guard**.
5. Read the Guard decision. If it says **ALLOW**, the payment buttons become available. Click **Approve & pay (demo)** to complete the simulated purchase.
6. A payment-success screen appears. **Go to home page** returns to the shoe catalog.
7. To inspect the saved journey, open the Pulse dashboard at `http://localhost:5174` if it is running. Search for merchant `STRIDEHUB` and open the newest journey.

If you did not include a budget in chat, the shop asks for the total budget in its UI first. It does not search products or create the purchase journey until you submit that budget. For variety, ask for **different** models explicitly, e.g. “Buy 2 different running shoes, one pair of each, budget $400.”

## 7. Use MCP Inspector for a deliberate negative test

Inspector lets you pick a tool and enter its arguments yourself. Use this when you want to demonstrate a Guard block instead of relying on the AI agent to choose a scenario.

### Launch and connect Inspector

1. Keep Mandate Lens, Merchant, and MCP running in their own windows.
2. Open another PowerShell window. You need Node.js 22.19 or newer.
3. Start Inspector and point it at the local MCP URL:

```powershell
npx -y @modelcontextprotocol/inspector --server-url http://127.0.0.1:8001/mcp --transport http
```

4. Inspector intentionally opens its own local browser page. This is the Inspector UI; the MCP server command itself does not open a page.
5. If the **Servers** page says **Disconnected**, use the toggle on the server card to connect. The address should be `http://127.0.0.1:8001/mcp`, as in your screenshots.
6. Click **Tools** in the top navigation (your third screenshot). You should see the available tools list.
7. Select `open_shoe_shop` and click its run/call control. For a browse smoke check, pass `purchase=false` and `query="running shoes"`; the result should contain/render the shop UI if Inspector supports the MCP App display. Inspector is primarily for calling tools manually; full shopper interaction is best shown in a compatible client such as Cursor.

### Create a fresh $200 journey in Inspector

Perform each step from the Inspector **Tools** tab. The tools marked UI-only for the AI model can still appear for manual Inspector calls.

1. Select `get_footwear_products`, run it, and note the exact product IDs for **Stride Pace** and **Stride Trail** from the catalog result.
2. Select `prepare_purchase_item` and enter this JSON (the `product_id` can remain blank; the search query selects Pace):

```json
{
  "max_amount": 200,
  "query": "Stride Pace",
  "quantity": 1,
  "product_id": "",
  "cart_id": "",
  "different_models": 1
}
```

3. Run the tool. Confirm `prepared` is `true` and copy `approvalId` from its result.
4. Select `start_shopping_session`, enter `{"approval_id":"PASTE_APPROVAL_ID_HERE"}`, and run it.
5. Copy `session.sessionId` and `session.journeyId` from the result. Check that `purchaseResult.decision` is `ALLOW` and the initial cart total is below $200. The tool creates the budgeted journey and adds the prepared Pace item.

### Trigger the over-budget block in Inspector

1. Select `add_item_to_demo_cart`.
2. Enter the Stride Trail product ID from step 1, quantity `1`, and session ID from step 5:

```json
{
  "product_id": "PASTE_STRIDE_TRAIL_PRODUCT_ID_HERE",
  "quantity": 1,
  "session_id": "PASTE_SESSION_ID_HERE"
}
```

3. Run the tool. With the demo catalog prices (Pace $79 and Trail $189), the proposed combined total is $268, above the $200 budget. Expect `ok: false`, `decision: BLOCK`, and a reason explaining the amount limit. The saved cart should remain at its original total. If your local prices differ, pick any product that makes the combined total exceed $200.
4. Select `get_purchase_journey_summary` and enter:

```json
{
  "journey_id": "PASTE_JOURNEY_ID_HERE",
  "merchant_id": "STRIDEHUB"
}
```

5. Run it. This read-only tool returns the saved Guard block and reason from Mandate Lens.
6. Open Pulse at `http://localhost:5174`, search for `STRIDEHUB`, and open the same journey ID. Confirm the timeline and Guard checks show the attempted operation and block.

Every action that creates the negative case is an MCP Inspector tool call. The `add_item_to_demo_cart` call asks Merchant to submit the proposed full cart to Mandate Lens/AgentGuard. Never call `approve_demo_checkout` for this negative test.

### Read the saved block through Inspector

1. Return to Inspector's **Tools** tab.
2. Select `get_purchase_journey_summary`.
3. Enter the journey ID from the $200 purchase and merchant ID `STRIDEHUB`.
4. Run it. This tool is read-only: it asks Mandate Lens for the recorded journey summary.
5. Check the output for the blocked Guard decision, its reason (cart amount exceeds the simulated limit), and the cart operation result.
6. Open the Pulse dashboard at `http://localhost:5174`, search merchant `STRIDEHUB`, and open that same journey. The timeline and Guard checks should show the attempted operation and block.

The summary lookup reads saved facts; the block is produced by the Inspector `add_item_to_demo_cart` call when AgentGuard evaluates the over-budget proposed cart.

## 8. Common issues

- **Inspector says Disconnected:** verify the MCP terminal is still running and the URL is exactly `http://127.0.0.1:8001/mcp`; toggle the server on in Inspector.
- **No tools appear:** connect first, then open **Tools**. In Cursor, reload the MCP server from its MCP settings and reopen the agent chat.
- **Shop UI does not appear in VS Code:** that client/extension may not render MCP Apps. Try Cursor for the UI demo; use Inspector for manual tool calling.
- **No product list / catalog error:** Merchant must be running on `8080`; check `http://127.0.0.1:8080/api/products`.
- **Journey or Guard service unavailable:** Mandate Lens must be running on `8081`.
- **A cart change was not blocked:** verify you used the journey's matching cart ID, the session budget was $200, and the proposed resulting total was over $200. A separate seeded limit in `.env` does not replace the budget stored on this newly created journey.
- **Database password failure:** check root `.env`; stop and restart both backend windows with `run-server.ps1` so they load the current values.
- **Stop the demo:** press `Ctrl+C` in each PowerShell window. Stop Inspector separately in its own window.

## Demo boundaries

This is a local hackathon demonstration. The shopper budget form simulates consent and is not a signed AP2 mandate. With PayPal Sandbox disabled, the MCP purchase path records a simulated Merchant payment. With Sandbox enabled, Guard must return ALLOW before the MCP flow creates a PayPal order; the buyer approves it in PayPal and Merchant captures it after the return. These are test transactions only. The Guard block is a deterministic local policy decision, not a claim that a production authorization or payment network verified the shopper.
