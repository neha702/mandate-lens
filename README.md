# AgentPulse MVP backend

Spring Boot 3 / Java 17 starter for purchase journey events. The current slice provides a health endpoint, an in-memory event store, a seeded demo journey, and APIs to read a timeline and add events.

## Run

Requirements: Java 17 and Maven 3.6.3 or later.

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. The in-memory store resets whenever the app restarts.

## Try the API

Health:

```powershell
curl.exe http://localhost:8080/api/health
```

Read the seeded journey:

```powershell
curl.exe http://localhost:8080/api/journeys/demo-shoes-001
```

Run the draft AgentGuard check against the seeded journey:

```powershell
curl.exe -X POST http://localhost:8080/api/journeys/demo-shoes-001/guard-check `
  -H "Content-Type: application/json" `
  -d '{"journeyId":"demo-shoes-001","shopperLimit":{"amount":200.00,"currency":"USD","source":"simulated_demo"}}'
```

The local demo Guard should return `BLOCK` for the seeded $209 total and include `evt_102` as evidence. Try changing `amount` from `200.00` to `220.00` to see an `ALLOW` result. This check is a replaceable mock for interface discussion; it is not the AgentGuard team's confirmed implementation or policy engine.

The seeded events `evt_101` and `evt_102` are explicitly labelled `simulated_demo`. They represent a cart total of $179 followed by a shipping update to $209. They are demo data, not Store Sync/AP2 records or PayPal-confirmed events.

Add an event:

```powershell
curl.exe -X POST http://localhost:8080/api/journeys/demo-shoes-001/events `
  -H "Content-Type: application/json" `
  -d '{"id":"evt_103","occurredAt":"2026-10-20T09:05:00Z","type":"checkout_started","source":"simulated_demo","amount":209.00,"currency":"USD","details":"Demo checkout started."}'
```

Events require an ID, timestamp, type, and source. Event IDs must be unique. Unknown journeys return 404; repeated IDs return 409.

## Current scope

- Included: Spring Boot REST API, request validation, normalized event shape, in-memory event store, chronological journey timeline, demo seed data.
- Included as a draft: an AgentGuard request/response shape, a replaceable `AgentGuardClient` boundary, and a local demo check for missing data, currency mismatch, and shopper limit.
- Next: replace the demo AgentGuard client with the other team's agreed service/module; add their mandate and merchant policy checks, then build the dashboard and AI explanation.
- Later: PayPal Sandbox create → buyer approval → server-side capture. Payment success must come from the backend-verified PayPal response, never a frontend flag or AI output.

No PayPal or AI credentials are needed to run this slice.
