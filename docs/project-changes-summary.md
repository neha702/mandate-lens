# What we have added, in simple terms

This is a plain-language summary of the current local demo work.

- **The shop uses the backend catalog.** PacePoint loads product names, prices, and images from the merchant API instead of maintaining its own separate product list.
- **Cart changes are checked before the merchant applies them.** The backend asks AgentGuard whether a proposed cart action can proceed, then records what the merchant actually did.
- **Pulse saves the purchase story.** The backend stores journey events and Guard decisions so the merchant can later see what happened and why an action was allowed or blocked.
- **The services and their UIs are split locally.** Merchant API/shop run on ports 8080/5173. Mandate Lens API/Pulse dashboard run on ports 8081/5174. Merchant calls Mandate Lens over HTTP for checks and outcome callbacks.
- **There is a Mandate Lens Pulse dashboard.** Open `http://localhost:5174` to list a merchant's journeys, filter/search them, and inspect the selected journey's event timeline and Guard decisions.
- **The demo catalog has more footwear.** The database migration and seed data add more running-shoe products for the shop and MCP demo.
- **The local MCP shop now uses a shopper-approved flow.** If no budget is in chat, the UI asks for it before searching Merchant. It then shows one matching affordable shoe and the requested quantity; the shopper approves that exact selection before a journey/cart change. If budget was given in chat, it searches right away but still asks the shopper to approve the selected item. Cart changes go through Merchant and AgentGuard; Pulse saves the events. Checkout has a separate Guard review and approve/decline. Only a successful payment completes the journey; the MCP session ends and the UI returns to the full Merchant footwear catalog. This is demo consent, not a signed AP2 credential or PayPal transaction.
- **Saved carts outlive journey authorization.** Mandate Lens now expires journeys when their linked mandate expires, not after 30 minutes of inactivity. It rejects new operations on expired journeys. Merchant can retain an open cart and attach it to a fresh journey after the shopper approves a new budget.
- **Starting the backend is easier on Windows.** `run-server.ps1` reads the private root `.env` file and starts Spring Boot with those settings. Do not run Maven directly if you expect it to load `.env`.
- **PayPal Sandbox is currently disabled.** With `PAYPAL_SANDBOX_ENABLED=false`, checkout is simulated locally. No PayPal credentials, external payment call, or real money movement is involved.
- **There is a step-by-step local setup guide.** See [windows-local-setup.md](windows-local-setup.md) for PostgreSQL, `.env`, backend, storefront, MCP, Cursor config, and prompts.

## What to run for each demo

- Shop and Pulse dashboard: start PostgreSQL, Mandate Lens, Merchant, then both UIs.
- Cursor footwear experience: start the backend and MCP server, enable the configured MCP server in Cursor, then prompt Cursor to show the footwear shop.
