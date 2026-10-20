# Mandate Lens

Mandate Lens now contains the Store Sync demo shop and the AgentPulse purchase journey backend. The backend serves the product list, records purchase events, shows a journey timeline, and exposes a draft AgentGuard check. The AgentGuard decision is currently a local demo implementation; it is not connected to the other team's service yet.

## Project layout

- `frontend/` — React shop UI.
- `backend/` — Spring Boot / Java 17 API, Store Sync product endpoint, AgentPulse journey API, and draft AgentGuard adapter.
- `AgentPulse_AgentGuard_Handoff.md` — proposed ownership split and draft interface for the teams to confirm.
- `AgentPulse_Hackathon_Starter_Guide.docx` — project and demo guide.

## Run the backend

Use Java 17+ and the included Maven wrapper. From the project root in PowerShell:

```powershell
.\mvnw.cmd -pl backend spring-boot:run
```

The API runs at `http://localhost:8080`.

## Run the shop frontend

Open a second terminal:

```powershell
cd frontend
npm install
$env:VITE_API_URL = "http://localhost:8080"
npm run dev
```

Open the local Vite URL shown in the terminal. The shop loads its product list from `GET /api/products`.

## Try the APIs

Health check:

```powershell
curl.exe http://localhost:8080/api/health
```

List shop products:

```powershell
curl.exe http://localhost:8080/api/products
```

Read the seeded purchase journey:

```powershell
curl.exe http://localhost:8080/api/journeys/demo-shoes-001
```

Run the draft AgentGuard check:

```powershell
curl.exe -X POST http://localhost:8080/api/journeys/demo-shoes-001/guard-check `
  -H "Content-Type: application/json" `
  -d '{"journeyId":"demo-shoes-001","shopperLimit":{"amount":200.00,"currency":"USD","source":"simulated_demo"}}'
```

The local demo checker returns `BLOCK` for the seeded $209 total and cites `evt_102`. Change the limit to `$220` to see `ALLOW`. This is sample logic; replace it with the AgentGuard team's confirmed interface and decision service.

Add a sample event to the journey:

```powershell
curl.exe -X POST http://localhost:8080/api/journeys/demo-shoes-001/events `
  -H "Content-Type: application/json" `
  -d '{"id":"evt_103","occurredAt":"2026-10-20T09:05:00Z","type":"checkout_started","source":"simulated_demo","amount":209.00,"currency":"USD","details":"Demo checkout started."}'
```

`add event` means recording something that happened in the purchase timeline; it does not add a product to the shopping cart. The current two seeded events are labelled `simulated_demo`, are not real store/AP2 records, and reset when the backend restarts.

## Current integration boundaries

- The shop gets product data from the backend's Store Sync product endpoint.
- AgentPulse currently uses seeded demo events. The React cart is not yet connected to the journey event API.
- `AgentGuardClient` is the replaceable connection point. Its current implementation is a local budget-checking demo, not the AgentGuard team's confirmed policy engine.
- PayPal Sandbox checkout is not connected yet. Later, the backend should create and capture orders and record payment status only from PayPal's server response.
- Event storage is in memory for the demo. Data resets when the backend restarts.
