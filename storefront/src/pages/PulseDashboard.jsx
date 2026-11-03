import { useCallback, useEffect, useMemo, useState } from "react";
import Navbar from "../components/Navbar";
import { getMerchantJourney, getMerchantJourneys } from "../services/ProductService";
import "./PulseDashboard.css";

const DEFAULT_MERCHANT = new URLSearchParams(window.location.search).get("merchantId") || "STRIDEHUB";

function pretty(value) {
  return String(value || "Unknown").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function dateLabel(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium", timeStyle: "short",
  }).format(date);
}

function decisionTone(value) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "ALLOW" || normalized === "SUCCEEDED" || normalized === "COMPLETED") return "good";
  if (normalized === "BLOCK" || normalized === "FAILED" || normalized === "ABANDONED") return "bad";
  return "neutral";
}

function PulseDashboard() {
  const [merchantId, setMerchantId] = useState(DEFAULT_MERCHANT);
  const [merchantInput, setMerchantInput] = useState(DEFAULT_MERCHANT);
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [journeys, setJourneys] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");

  const loadJourneys = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const rows = await getMerchantJourneys(merchantId, { status, search });
      setJourneys(rows || []);
      setSelectedId((current) => rows?.some((journey) => journey.journeyId === current)
        ? current : rows?.[0]?.journeyId || "");
    } catch (loadError) {
      setError(`${loadError.message} Check that the backend is running at http://localhost:8080 and that this merchant ID has journey data.`);
      setJourneys([]);
    } finally {
      setLoading(false);
    }
  }, [merchantId, status, search]);

  useEffect(() => { void loadJourneys(); }, [loadJourneys]);

  useEffect(() => {
    if (!selectedId) { setDetail(null); return; }
    let active = true;
    setDetailLoading(true);
    getMerchantJourney(selectedId, merchantId)
      .then((data) => { if (active) setDetail(data); })
      .catch((loadError) => { if (active) setError(loadError.message); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId, merchantId]);

  const metrics = useMemo(() => ({
    total: journeys.length,
    active: journeys.filter((journey) => journey.status === "ACTIVE").length,
    blocked: journeys.filter((journey) => journey.status === "BLOCKED" || journey.latestDecision === "BLOCK").length,
    completed: journeys.filter((journey) => journey.status === "COMPLETED").length,
  }), [journeys]);

  function submitMerchant(event) {
    event.preventDefault();
    const next = merchantInput.trim();
    if (!next) return;
    setMerchantId(next);
    const query = new URLSearchParams(window.location.search);
    query.set("merchantId", next);
    window.history.replaceState({}, "", `${window.location.pathname}?${query.toString()}`);
  }

  return (
    <div className="store-app pulse-app">
      <Navbar cartCount={0} onCartClick={() => {}} pulseActive />
      <main className="pulse-main">
        <section className="pulse-heading">
          <div>
            <p className="eyebrow">MERCHANT TOOLS · AGENTPULSE</p>
            <h1>Purchase journeys</h1>
            <p>See what happened across AI-assisted shopping: cart checks, merchant results, and payment outcomes.</p>
          </div>
          <form className="merchant-picker" onSubmit={submitMerchant}>
            <label htmlFor="pulse-merchant">Merchant</label>
            <div><input id="pulse-merchant" value={merchantInput} onChange={(event) => setMerchantInput(event.target.value)} aria-label="Merchant ID" />
              <button type="submit">View</button></div>
          </form>
        </section>

        {error && <div className="pulse-alert" role="alert"><strong>Could not load journey data.</strong><span>{error}</span><button onClick={() => void loadJourneys()}>Try again</button></div>}

        <section className="pulse-metrics" aria-label="Journey totals">
          <article><span>Journeys shown</span><strong>{metrics.total}</strong><small>Latest 100 for this merchant</small></article>
          <article><span>Active</span><strong>{metrics.active}</strong><small>Still in progress</small></article>
          <article><span>Blocked</span><strong>{metrics.blocked}</strong><small>Guard blocked or journey marked blocked</small></article>
          <article><span>Completed</span><strong>{metrics.completed}</strong><small>Finished journeys</small></article>
        </section>

        <section className="pulse-workspace">
          <div className="journey-list-panel">
            <div className="pulse-list-heading"><div><p className="eyebrow">SAVED ACTIVITY</p><h2>All journeys</h2></div><button className="refresh-button" onClick={() => void loadJourneys()} aria-label="Refresh journeys">↻</button></div>
            <div className="journey-filters">
              <label className="search-field"><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search journey, cart, or order" /></label>
              <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter journeys by status">
                <option value="">All statuses</option><option>ACTIVE</option><option>COMPLETED</option><option>BLOCKED</option><option>FAILED</option><option>ABANDONED</option><option>EXPIRED</option>
              </select>
            </div>
            <div className="journey-list" aria-live="polite">
              {loading && <div className="pulse-empty">Loading this merchant’s journeys…</div>}
              {!loading && journeys.length === 0 && <div className="pulse-empty"><strong>No journeys found</strong><span>Try another merchant ID or clear the filters.</span></div>}
              {!loading && journeys.map((journey) => (
                <button key={journey.journeyId} className={`journey-row ${selectedId === journey.journeyId ? "selected" : ""}`} onClick={() => setSelectedId(journey.journeyId)}>
                  <span className={`journey-dot ${decisionTone(journey.latestDecision || journey.status)}`} />
                  <span className="journey-row-copy"><strong>{journey.journeyId}</strong><small>{journey.cartId || "No cart ID"} · {pretty(journey.latestEventType || "No events yet")}</small></span>
                  <span className={`status-pill ${decisionTone(journey.status)}`}>{pretty(journey.status)}</span>
                  <time>{dateLabel(journey.lastEventAt || journey.startedAt)}</time>
                </button>
              ))}
            </div>
          </div>

          <aside className="journey-detail-panel">
            {!selectedId && <div className="pulse-empty detail-empty"><strong>Select a journey</strong><span>Its history and Guard decisions will appear here.</span></div>}
            {selectedId && (detailLoading || !detail) && <div className="pulse-empty detail-empty">Loading journey details…</div>}
            {detail && !detailLoading && <>
              <div className="detail-title-row"><div><p className="eyebrow">JOURNEY DETAILS</p><h2>{detail.journeyId}</h2></div><span className={`status-pill ${decisionTone(detail.status)}`}>{pretty(detail.status)}</span></div>
              <div className="detail-facts"><div><span>Merchant</span><strong>{detail.merchantId}</strong></div><div><span>Cart</span><strong>{detail.cartId || "—"}</strong></div><div><span>Started</span><strong>{dateLabel(detail.startedAt)}</strong></div><div><span>Last activity</span><strong>{dateLabel(detail.lastEventAt)}</strong></div></div>
              <div className="detail-section"><div className="detail-section-heading"><h3>Activity timeline</h3><span>{detail.timeline?.length || 0} events</span></div>
                {!detail.timeline?.length && <p className="muted-copy">No activity has been saved for this journey yet.</p>}
                <ol className="pulse-timeline">{(detail.timeline || []).map((event) => <li key={event.eventId}>
                  <span className={`timeline-marker ${decisionTone(event.outcome)}`} />
                  <div className="timeline-copy"><div><strong>{pretty(event.type)}</strong><time>{dateLabel(event.occurredAt)}</time></div><small>{pretty(event.source)}{event.outcome ? ` · ${pretty(event.outcome)}` : ""}</small>
                    {event.details && <details><summary>Show event details</summary><pre>{JSON.stringify(event.details, null, 2)}</pre></details>}
                  </div>
                </li>)}</ol>
              </div>
              <div className="detail-section decision-section"><div className="detail-section-heading"><h3>Guard checks</h3><span>{detail.decisions?.length || 0} decisions</span></div>
                {!detail.decisions?.length && <p className="muted-copy">No Guard decisions recorded for this journey.</p>}
                {(detail.decisions || []).map((decision) => <details className="guard-decision" key={decision.decisionId}><summary><span className={`decision-chip ${decisionTone(decision.decision)}`}>{decision.decision}</span><strong>{pretty(decision.action)}</strong><time>{dateLabel(decision.evaluatedAt)}</time></summary>
                  <p>Decision ID: {decision.decisionId}</p>{decision.reasons && <pre>{JSON.stringify(decision.reasons, null, 2)}</pre>}
                  {decision.ruleResults?.length > 0 && <ul>{decision.ruleResults.map((rule, index) => <li key={`${rule.ruleId || "rule"}-${index}`}><b>{rule.result || rule.status || "CHECK"}</b> · {rule.message || rule.ruleId || "Rule"}</li>)}</ul>}
                </details>)}
              </div>
            </>}
          </aside>
        </section>
        <p className="pulse-footnote">This page is the merchant’s Pulse view. It reads journey history from Mandate Lens; it does not create or change carts or payments.</p>
      </main>
    </div>
  );
}

export default PulseDashboard;
