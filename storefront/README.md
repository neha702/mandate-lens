# PacePoint merchant UI and AgentPulse dashboard

This React application contains the PacePoint demo shop and merchant-facing Pulse journey dashboard. For complete Windows PowerShell steps, including `.env`, the backend, MCP, and Cursor setup, see [../docs/windows-local-setup.md](../docs/windows-local-setup.md).

## Start it locally

1. Start PostgreSQL and make sure the mandatelens database exists.
2. From the repository root, start the Spring Boot API:

   ```powershell
   .\run-server.ps1
   ```

   The ignored root `.env` is loaded by this script. Spring Boot does not read that file automatically.

3. Keep `PAYPAL_SANDBOX_ENABLED=false` in the root `.env` while Sandbox credentials are being checked. This simulates checkout locally; no PayPal credentials are needed. Restart the backend after changing `.env`.

4. In a second PowerShell terminal, run:

   ```powershell
   Set-Location "C:\Users\prati\OneDrive\Desktop\Hackathon\mandate-lens\storefront"
   npm ci
   npm run dev
   ```

5. Open the local Vite URL printed in the terminal, usually http://localhost:5173.

The Pulse merchant dashboard is at `http://localhost:5173/pulse`. It lists journeys for the merchant ID you select (defaults to `STRIDEHUB`) and shows the event history and Guard decisions for a selected journey.

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

PayPal Sandbox checkout is not part of the current demo path. When the team has verified Sandbox credentials and is ready to test it, see [../docs/local-e2e.md](../docs/local-e2e.md) for the optional setup. Never put Live credentials in the local demo configuration.
