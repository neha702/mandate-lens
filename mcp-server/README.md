# Mandate Lens Merchant MCP demo

This is a separate, local FastMCP project. You start it as a local HTTP MCP server, then Cursor connects to it. Footwear lookups call the merchant's live `GET /api/products` endpoint; the MCP App displays those catalog records and product images inside Cursor. The MCP server embeds images from the approved Unsplash image host into the app response to avoid remote-image restrictions in the MCP UI. If an image cannot be fetched, it uses a local illustration so the card does not show a broken image. The demo cart, budget, and simple allow/block check still live in MCP memory and reset when the server process restarts.

**Only product browsing is connected.** Cart changes and their allow/block result are still a UI prototype; they do not call the merchant cart API, AgentGuard, or Mandate Lens yet. The merchant must perform the Mandate Lens check and report its actual result; the MCP server must not bypass the merchant and call Guard directly.

## Start the local MCP server

Open PowerShell in this directory and run:

```powershell
uv sync
uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001
```

Start the Spring Boot merchant API first (usually `http://127.0.0.1:8080`), then leave this terminal running. The MCP server listens at `http://127.0.0.1:8001/mcp` and does not open a webpage. Set `MERCHANT_API_BASE_URL` if the API uses another address.

## Connect Cursor to this local server

The repository root has a `.cursor/mcp.json` file that points Cursor to the local endpoint. Start both servers, then reload MCP servers in Cursor. For a text list, ask Cursor to find or compare running shoes; it should call `get_footwear_products`, which fetches the live merchant catalog. For the visual experience, ask: **“Show me the footwear shop.”** Cursor should call `open_shoe_shop` and render the catalog images in chat. Product-card cart actions still use the local demo cart.

If your Cursor version or project settings do not load the repository MCP configuration, open Cursor's MCP settings and add a server URL: `http://127.0.0.1:8001/mcp`.

## Important: do not use the browser-preview command

Do not run `fastmcp dev apps server.py` for the Cursor demo. That is a UI development preview and intentionally opens a browser. For the normal demo, use only `uv run fastmcp run server.py --transport http --host 127.0.0.1 --port 8001` above. It starts the MCP endpoint without opening a webpage.

## Demo MCP tools

- `get_footwear_products`: fetches products from the merchant API. The tool description tells the AI to call it only for explicit footwear browsing, listing, comparison, or finding requests.
- `open_shoe_shop`: fetches the same live footwear catalog and opens the product-card UI. Use for footwear shopping/browsing requests.
- `add_item_to_demo_cart`: refreshes product details from the merchant API, then simulates a local cart change and budget check.
- `reset_demo_cart`: clears the in-memory cart.

## Merchant API connection

The product API is `GET /api/products`. The MCP server filters its results to footwear categories and returns the merchant's IDs, descriptions, prices, availability, and image URLs. The merchant currently comes from `http://127.0.0.1:8080` by default. Cart actions are not yet connected: the next integration should call the merchant cart APIs so the merchant can run Mandate Lens checks and report outcomes. Keep PayPal Sandbox checkout behind the merchant's checkout flow.

If the merchant API is unavailable, the MCP tool returns an error instead of silently showing stale hardcoded product data. No PayPal credentials are used by this product lookup.
