# Local demo setup on Windows (PowerShell)

This guide starts the three local pieces used in the demo:

1. **Mandate Lens backend** — Spring Boot API, AgentPulse/AgentGuard orchestration, merchant catalog/cart, and journey storage.
2. **PacePoint merchant UI** — the shop at `http://localhost:5173`.
3. **Footwear MCP server** — lets Cursor ask the backend for footwear products and render the product shop as an MCP app.

PayPal Sandbox is **off** in the demo configuration. Checkout is simulated locally and does not contact PayPal or move money. Do not turn Sandbox on until the team has checked the Sandbox app credentials.

## One-time setup

### 1. Confirm the local tools

Install Java 17 or later, Node.js/npm, PostgreSQL, Python, and `uv`. From the repository root, confirm the Maven wrapper exists (`mvnw.cmd`).

### 2. Create the PostgreSQL database

Start the PostgreSQL service. In pgAdmin, connect to your local PostgreSQL server, right-click **Databases**, choose **Create → Database**, and name it `mandatelens`. The backend will create its tables when it first starts.

If the database already exists, do not create a duplicate.

### 3. Create the private `.env` file

From the repository root, create `.env` from the example only if you do not already have one:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` to match your local PostgreSQL installation. Keep `.env` private; it is ignored by Git. Do not paste its password into chat or commit it.

For this demo leave these settings as shown:

```dotenv
SPRING_PROFILES_ACTIVE=storesync-simulator,agentguard-deterministic
PAYPAL_SANDBOX_ENABLED=false
```

The first setting selects local Store Sync and deterministic demo Guard behavior. The second means checkout is simulated locally. Leave the PayPal client ID and secret blank until Sandbox credentials are verified.

## Start the backend

1. Open PowerShell and go to the repository root:

   ```powershell
   Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens"
   ```

2. Start Spring Boot with the repository script:

   ```powershell
   .\run-server.ps1
   ```

3. Leave this PowerShell window open. Wait for Spring Boot to report that it started. The API listens on `http://localhost:8080`.

### What the `.env` script does

PowerShell does not automatically read `.env` files. `run-server.ps1` reads each `NAME=value` line from the repository-root `.env`, places those values in the environment of the Maven/Spring process, checks that the three database settings are present, and launches the Maven wrapper. It does not print the password. This is why the script should be used instead of running `mvnw.cmd` directly: a direct Maven command would not automatically load `.env`.

If the database password changes, edit `.env`, stop the backend with **Ctrl+C**, then run `.\run-server.ps1` again in a new or existing PowerShell window. The script loads the latest file contents each time.

Check that the backend is responding from another PowerShell window:

```powershell
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/products
```

## Start and view the merchant UI

Open a second PowerShell window. The backend should stay running in the first one.

```powershell
Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\storefront"
npm ci
npm run dev
```

Run `npm ci` once, or again only when frontend dependencies change. Leave this window open. Open the Vite URL shown in the terminal, normally:

```text
http://localhost:5173
```

The shop reads products and carts from the local Spring Boot API. The **Pulse dashboard** link in the shop header opens the merchant dashboard at:

```text
http://localhost:5173/pulse
```

The dashboard defaults to demo merchant `STRIDEHUB`. It reads the merchant's journeys from Mandate Lens. Use the merchant field on the page to view another merchant ID that has journey data.

## Start the MCP server from PowerShell

This step is for Cursor's AI shopping demo. It is separate from the browser-based merchant UI.

1. Make sure the backend is running, because footwear product lookup calls its `GET /api/products` endpoint.
2. Open a third PowerShell window and run:

   ```powershell
   Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\mcp-server"
   uv sync
   uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
   ```

3. Leave this window open. The server listens at `http://127.0.0.1:8001/mcp`. This command starts the MCP endpoint only; it does **not** open a webpage. Do not use `fastmcp dev apps server.py` for normal Cursor use because that command opens the FastMCP UI preview in a browser.

## Connect or reload the MCP server in Cursor

The project contains `.cursor/mcp.json`. If Cursor is open on this repository, it should discover that file. Its contents are:

```json
{
  "mcpServers": {
    "mandate-lens-demo": {
      "url": "http://127.0.0.1:8001/mcp"
    }
  }
}
```

If adding it manually in Cursor, open **Cursor Settings → MCP**, choose **Add new MCP server**, select an HTTP/Streamable HTTP server if asked, and use the URL above. If Cursor asks for a JSON config instead, copy the complete JSON block above into the project MCP configuration. Save it, then use Cursor's MCP **Reload/Refresh** action (or restart Cursor if that option is missing). In the MCP settings list, switch the toggle beside `mandate-lens-demo` on if it is off. Confirm it shows as connected and its tools are available.

Do not add the URL as a browser page. Cursor connects to it as an MCP server.

## Ask Cursor to show the footwear UI

In Cursor's AI chat, type:

> Show me the footwear shop.

Cursor should call the `open_shoe_shop` MCP tool. The footwear list comes from the running merchant backend, and the MCP app displays the product cards in Cursor. To get a text list instead, ask:

> Find running shoes under 150 dollars.

That should call `get_footwear_products`. Product lookup is designed to be used for footwear-related browsing requests; unrelated prompts should not need the catalog tool.

The MCP demo's cart actions are still a prototype stored in MCP process memory. They are not the same as the PacePoint browser cart and are not yet connected to Merchant Cart, AgentGuard, or Mandate Lens operation checks. Restarting the MCP process clears that prototype cart.

## Recommended startup order

Keep three terminal windows open:

1. PowerShell 1: `.\run-server.ps1` from the repository root.
2. PowerShell 2: `npm run dev` from `storefront` — then open `http://localhost:5173` or `/pulse`.
3. PowerShell 3: `uv run fastmcp run ...` from `mcp-server` — then ask Cursor to show the footwear shop.

The first two are enough to use the shop and Pulse dashboard. Start the third only when demonstrating the Cursor MCP shopping app.

## Stop the demo

In each running PowerShell window, press **Ctrl+C**. Stop the frontend and MCP server first, then the backend. PostgreSQL can remain running.

## Troubleshooting

- **Database password authentication failed:** check `DATABASE_PASSWORD` in the root `.env` against the PostgreSQL password, then restart with `.\run-server.ps1`. Do not rely on a password variable set in a different terminal.
- **Port 8080 is unavailable:** another backend may already be running; stop it or resolve the port conflict before starting another copy.
- **Products do not load:** verify the backend health URL and `http://localhost:8080/api/products` before refreshing the shop.
- **Pulse page says it cannot load:** backend must be running and the merchant ID must be one that has journeys. The demo merchant ID is `STRIDEHUB`.
- **Cursor says MCP disconnected:** confirm the third PowerShell window is still running, check the URL and port, then reload MCP servers in Cursor.
- **MCP product images are unavailable:** the MCP server attempts to fetch catalog image URLs and falls back to local shoe illustrations when remote image fetching is unavailable. Restart the MCP server after code changes.
- **PayPal asks for credentials or checkout cannot proceed:** confirm `PAYPAL_SANDBOX_ENABLED=false` in `.env`, then restart the backend. This is the current planned mode while Sandbox credentials are being checked.
