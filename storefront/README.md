# PacePoint merchant shop UI

This React application contains the PacePoint demo shop only. The Pulse journey dashboard is a separate Mandate Lens UI in `../mandate-lens-ui`. For complete Windows PowerShell steps, including `.env`, both APIs, both UIs, MCP, and Cursor setup, see [../docs/windows-local-setup.md](../docs/windows-local-setup.md).

## Start it locally

1. Start PostgreSQL and make sure the mandatelens database exists.
2. From the repository root, start the Spring Boot API:

   ```powershell
   .\run-server.ps1 -Service Merchant
   ```

   The ignored root `.env` is loaded by this script. Spring Boot does not read that file automatically.

3. Keep `PAYPAL_SANDBOX_ENABLED=false` in the root `.env` for simulated checkout. To use PayPal Sandbox from the storefront or MCP checkout, set it to `true` and configure the Sandbox REST app credentials and return/cancel URLs as described in [../docs/local-e2e.md](../docs/local-e2e.md). Restart the backend after changing `.env`.

4. In a second PowerShell terminal, run:

   ```powershell
   Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\storefront"
   npm ci
   npm run dev
   ```

5. Open the local Vite URL printed in the terminal, usually http://localhost:5173.

The separate Mandate Lens Pulse dashboard runs at `http://localhost:5174`. Use the **Pulse dashboard** link in the shop header. It lists journeys for the merchant ID you select (defaults to `STRIDEHUB`) and shows the event history and Guard decisions for a selected journey. Mandate Lens API must also be running on port 8081.

The Vite development server proxies /api and /merchant-cart to http://localhost:8080. Do not set VITE_API_URL for this local setup.

## Merchant APIs used by the UI

- GET /api/products — load the PostgreSQL-backed catalog.
- POST /merchant-cart — create the cart on the first add.
- GET /merchant-cart/{cartId} — restore the saved cart after a page reload.
- PUT /merchant-cart/{cartId} — apply quantity, removal, and shipping changes.
- POST /merchant-cart/{cartId}/checkout — ask the backend to check the cart and complete either the local simulated payment or PayPal Sandbox capture.

The backend owns cart prices, Guard checks, PayPal credentials, and order capture. The browser never receives the PayPal client secret.

## Payment modes

With `PAYPAL_SANDBOX_ENABLED=false`, the UI labels checkout as a local demo. The backend still performs Guard checks but does not call PayPal; completion is simulated and no money moves.

With PayPal Sandbox enabled, the backend creates Orders v2 orders and returns PayPal's approval URL. The storefront redirects the buyer to PayPal, then captures the approved order after PayPal returns. The MCP shop uses the same Merchant order and capture path after Guard allows checkout. Sandbox uses test accounts; never put Live credentials in the local demo configuration.
