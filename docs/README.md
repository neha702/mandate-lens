# Project documentation

This folder contains the maintained, shareable documentation for the repository. Keep contributor-facing setup, interfaces, architecture, and operating procedures here. `admin-docs/` is a local-only archive and is intentionally excluded from Git.

## Current guides

- [End-to-end shopping flow](end-to-end-shopping-flow.md) — run the services, test the MCP purchase path, and optionally complete a PayPal Sandbox checkout.
- [Submission checklist](submission-checklist.md) — project description, tool attribution, functional demo, license, repository, and video requirements.

## Recommended documentation to add

1. **Architecture overview** — service ownership, request flow, data ownership, and a small component diagram.
2. **API reference** — endpoints, request/response examples, error meanings, and which service owns each route.
3. **Local development** — prerequisites, database setup, environment variables, start commands, and ports.
4. **Deployment and operations** — Render service names, root directories, build/start commands, environment-variable ownership, health checks, logs, and static-site rewrites.
5. **Data model** — schema ownership, migrations, important identifiers, and retention/lifecycle rules.
6. **Decision records** — short notes for choices with lasting impact, such as sharing a Maven module or database between services.

Write one maintained page per subject. Link to the relevant source code and keep runnable examples in sync with the implementation. Never include credentials, personal machine paths, or copied secrets.

## Naming

The customer-facing shop is **PacePoint**. `STRIDEHUB` is the demo merchant ID used by APIs and saved journey records; it is not the shop's display name.
