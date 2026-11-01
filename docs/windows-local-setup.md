# Local demo setup on Windows (PowerShell)

The demo now runs as separate local pieces:

1. **Mandate Lens API** — AgentPulse journeys, AgentGuard checks/orchestration, mandate records; port `8081`.
2. **Merchant API** — PacePoint catalog, cart, and payment adapter; port `8080`.
3. **Merchant shop UI** — port `5173`.
4. **Mandate Lens Pulse dashboard** — a separate UI owned by Mandate Lens; port `5174`.
5. **Footwear MCP server** — optional Cursor demo; port `8001`.

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

## Optional: start the MCP server and connect Cursor

Make sure Merchant is running because footwear lookup calls Merchant's `GET /api/products`. In a fifth PowerShell window:

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\mcp-server"
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Keep the window open. In Cursor MCP settings, use `http://127.0.0.1:8001/mcp`, reload/refresh the server, and enable its toggle. Then prompt Cursor: “Show me the footwear shop.” This MCP cart is still a separate in-memory prototype; it is not yet connected to the Merchant cart or Guard flow.

## Recommended startup order and ports

Start Mandate Lens (`8081`), Merchant (`8080`), Merchant shop (`5173`), and Pulse dashboard (`5174`). Start MCP (`8001`) only for the Cursor demo. Stop each with **Ctrl+C** in its own PowerShell window.

## Troubleshooting

- **Database password error:** verify `DATABASE_PASSWORD` in root `.env`, then stop and restart both backend processes with `run-server.ps1`.
- **Port already in use:** stop the other process already using `8080`, `8081`, `5173`, `5174`, or `8001`.
- **Merchant shop cannot load products:** check Merchant health at `http://localhost:8080/api/health` and catalog at `http://localhost:8080/api/products`.
- **Merchant says Mandate Lens is unavailable:** check `http://localhost:8081/api/health` and confirm it started before Merchant.
- **Dashboard cannot load journeys:** check Mandate Lens health at port `8081` and select a merchant ID that has journey history (demo default: `STRIDEHUB`).
- **MCP disconnected:** confirm its terminal is still running and Cursor is configured for `http://127.0.0.1:8001/mcp`.
