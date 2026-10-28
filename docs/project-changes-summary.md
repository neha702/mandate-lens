# What we have added, in simple terms

This is a plain-language summary of the current local demo work.

- **The shop uses the backend catalog.** PacePoint loads product names, prices, and images from the merchant API instead of maintaining its own separate product list.
- **Cart changes are checked before the merchant applies them.** The backend asks AgentGuard whether a proposed cart action can proceed, then records what the merchant actually did.
- **Pulse saves the purchase story.** The backend stores journey events and Guard decisions so the merchant can later see what happened and why an action was allowed or blocked.
- **There is a merchant Pulse dashboard.** Open `/pulse` in the storefront to list a merchant's journeys, filter/search them, and inspect the selected journey's event timeline and Guard decisions. It uses the existing journey APIs.
- **The demo catalog has more footwear.** The database migration and seed data add more running-shoe products for the shop and MCP demo.
- **The local MCP server can show live footwear.** Cursor connects to the FastMCP server; footwear prompts use the merchant product API and the MCP app displays product cards. Its cart is still a separate prototype and is not wired to the merchant cart or Guard.
- **Starting the backend is easier on Windows.** `run-server.ps1` reads the private root `.env` file and starts Spring Boot with those settings. Do not run Maven directly if you expect it to load `.env`.
- **PayPal Sandbox is currently disabled.** With `PAYPAL_SANDBOX_ENABLED=false`, checkout is simulated locally. No PayPal credentials, external payment call, or real money movement is involved.
- **There is a step-by-step local setup guide.** See [windows-local-setup.md](windows-local-setup.md) for PostgreSQL, `.env`, backend, storefront, MCP, Cursor config, and prompts.

## What to run for each demo

- Shop and Pulse dashboard: start PostgreSQL, then backend, then the storefront UI.
- Cursor footwear experience: start the backend and MCP server, enable the configured MCP server in Cursor, then prompt Cursor to show the footwear shop.
