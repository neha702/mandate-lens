# Project documentation

This folder contains the maintained, shareable documentation for the repository. Keep contributor-facing setup, interfaces, architecture, and operating procedures here. `admin-docs/` is a local-only archive and is intentionally excluded from Git.

## Current guides

- [Architecture overview](architecture.md) — component responsibilities, request flow, and data ownership.
- [Run and end-to-end guide](end-to-end-shopping-flow.md) — clone and run each local service or connect to the hosted demo, then complete the MCP purchase path using PayPal Sandbox checkout.

## Optional additions

Add these only if they help someone run, evaluate, or contribute to the project:

- **Deployment and operations** — hosted service URLs, build/start commands, environment-variable ownership, health checks, logs, and static-site rewrites. Render-specific MCP notes currently live in [`mcp-server/README.md`](../mcp-server/README.md).
- **API reference** — routes, request/response examples, error meanings, and the service that owns each route, if judges or contributors need to call APIs directly.
- **Data model or decision records** — schema ownership, migration lifecycle, and decisions with lasting impact, if the architecture needs more detail than [`architecture.md`](architecture.md).

Write one maintained page per subject. Link to the relevant source code and keep runnable examples in sync with the implementation. Never include credentials, personal machine paths, or copied secrets.

## Naming

The customer-facing shop is **PacePoint**. `STRIDEHUB` is the demo merchant ID used by APIs and saved journey records; it is not the shop's display name.
