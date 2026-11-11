import { useCallback, useEffect, useMemo, useState } from "react";
import { getMerchantJourney, getMerchantJourneys } from "../services/PulseApi";
import "./PulseDashboard.css";

const DEFAULT_MERCHANT = new URLSearchParams(window.location.search).get("merchantId") || "STRIDEHUB";

function pretty(value) {
  return String(value || "Unknown").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function dateLabel(value, options = { dateStyle: "medium", timeStyle: "short" }) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, options).format(date);
}

function decisionTone(value) {
  const normalized = String(value || "").toUpperCase();
  if (["ALLOW", "SUCCEEDED", "COMPLETED", "ALLOWED"].includes(normalized)) return "good";
  if (["BLOCK", "FAILED", "ABANDONED", "BLOCKED"].includes(normalized)) return "bad";
  return "neutral";
}

function readableReason(value) {
  if (!value) return "No reason supplied";
  if (typeof value === "string") return value.replaceAll("_", " ");
  if (Array.isArray(value)) return value.map(readableReason).join(" · ");
  if (typeof value === "object") {
    const message = value.message || value.reason || value.description || value.code?.replaceAll("_", " ");
    if (message) return message;
    const entries = Object.entries(value).filter(([, item]) => item !== null && item !== "");
    return entries.length ? entries.map(([key, item]) => `${pretty(key)}: ${readableReason(item)}`).join(" · ") : "Rule finding recorded";
  }
  return String(value);
}

function PulseDashboard() {
  const [merchantId, setMerchantId] = useState(DEFAULT_MERCHANT);
  const [merchantInput, setMerchantInput] = useState(DEFAULT_MERCHANT);
  const [status, setStatus] = useState("");
  const [attentionOnly, setAttentionOnly] = useState(false);
  const [journeyKind, setJourneyKind] = useState("");
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
      const rows = await getMerchantJourneys(merchantId, { status: journeyKind === "ATTENTION" ? "" : status, search });
      setJourneys(rows || []);
      setSelectedId((current) => rows?.some((journey) => journey.journeyId === current)
        ? current : rows?.[0]?.journeyId || "");
    } catch (loadError) {
      setError(`${loadError.message} Check the Mandate Lens API URL and that this merchant ID has journey data.`);
      setJourneys([]);
    } finally {
      setLoading(false);
    }
  }, [merchantId, status, search, journeyKind]);

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
    allowed: journeys.filter((journey) => journey.latestDecision === "ALLOW").length,
  }), [journeys]);

  const attentionJourneys = useMemo(() => journeys.filter((journey) =>
    journey.status === "BLOCKED" || journey.status === "FAILED" || journey.latestDecision === "BLOCK"
      || journey.latestEventType === "OPERATION_TIMED_OUT" || journey.latestOutcome === "FAILED"), [journeys]);

  const journeyCounts = useMemo(() => ({
    all: journeys.length,
    attention: attentionJourneys.length,
    active: journeys.filter((journey) => journey.status === "ACTIVE").length,
    completed: journeys.filter((journey) => journey.status === "COMPLETED").length,
  }), [journeys, attentionJourneys]);

  const visibleJourneys = useMemo(() => attentionOnly
    ? journeys.filter((journey) => attentionJourneys.some((row) => row.journeyId === journey.journeyId))
    : journeys, [journeys, attentionOnly, attentionJourneys]);

  const keyReason = useMemo(() => {
    const latestBlocked = [...(detail?.decisions || [])].reverse().find((decision) => decision.decision === "BLOCK");
    const firstRule = latestBlocked?.ruleResults?.find((rule) => rule.result === "BLOCK" || rule.status === "BLOCK" || rule.message);
    return firstRule ? readableReason(firstRule.message || firstRule.reason || firstRule.ruleId)
      : latestBlocked ? readableReason(latestBlocked.reasons) : "No authorization exception recorded";
  }, [detail]);

  function submitMerchant(event) {
    event.preventDefault();
    const next = merchantInput.trim();
    if (!next) return;
    setMerchantId(next);
    const query = new URLSearchParams(window.location.search);
    query.set("merchantId", next);
    window.history.replaceState({}, "", `${window.location.pathname}?${query.toString()}`);
  }

  function selectJourney(journeyId) {
    setSelectedId(journeyId);
    setDetail(null);
  }

  function chooseQueue(kind) {
    setJourneyKind(kind);
    setAttentionOnly(kind === "ATTENTION");
    setStatus(kind === "ACTIVE" || kind === "COMPLETED" ? kind : "");
  }

  return (
    <div className="pulse-app">
      <header className="ml-navbar">
        <a className="ml-brand" href="/" aria-label="Mandate Lens AgentPulse dashboard">
          <span className="ml-brand-symbol">M</span>
          <span>MANDATE LENS<small>MERCHANT TRUST</small></span>
        </a>
        <label className="global-search"><span>⌕</span><input placeholder="Search journeys, carts, or decisions…" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <div className="nav-right"><span className="nav-live"><i /> Connected</span><span className="merchant-avatar top-avatar" aria-label={`Merchant ${merchantId}`}>{merchantId.slice(0, 1).toUpperCase()}</span></div>
      </header>
      <div className="dashboard-shell">
      <main className="pulse-main">
        <section className="pulse-heading">
          <div>
            <p className="eyebrow">MERCHANT TRUST &amp; SAFETY <span className="crumb-sep">/</span> OVERVIEW</p>
            <h1>Agent activity, in context.</h1>
            <p>See what shopping agents proposed, how authorization checks responded, and what happened next.</p>
          </div>
          <form className="merchant-picker" onSubmit={submitMerchant}>
            <label htmlFor="pulse-merchant">Viewing merchant</label>
            <div><span className="merchant-avatar">{merchantId.slice(0, 1).toUpperCase()}</span><input id="pulse-merchant" value={merchantInput} onChange={(event) => setMerchantInput(event.target.value)} aria-label="Merchant ID" /><button type="submit">Switch</button></div>
          </form>
        </section>

        {error && <div className="pulse-alert" role="alert"><strong>Could not load journey data.</strong><span>{error}</span><button onClick={() => void loadJourneys()}>Try again</button></div>}

        <section className="pulse-overview" aria-label="Merchant journey overview">
          <div className="overview-label"><span>JOURNEY OVERVIEW</span><span className="window-label">LATEST 100 · ALL TIME</span></div>
          <div className="pulse-metrics">
            <article className="metric-total"><span>Journeys monitored</span><strong>{loading ? "—" : metrics.total}</strong><small><i className="metric-trend">↗</i> Shopping sessions recorded</small><span className="metric-glyph">◷</span></article>
            <article className="metric-attention"><span>Need attention</span><strong>{loading ? "—" : attentionJourneys.length}</strong><small>{attentionJourneys.length ? "Blocked or failed activity" : "No exceptions to review"}</small><span className="metric-glyph">!</span></article>
            <article className="metric-allowed"><span>Latest checks allowed</span><strong>{loading ? "—" : metrics.allowed}</strong><small>Within recorded authorization</small><span className="metric-glyph">✓</span></article>
            <article className="metric-completed"><span>Completed</span><strong>{loading ? "—" : metrics.completed}</strong><small>Purchase journey finished</small><span className="metric-glyph">↗</span></article>
          </div>
          <div className="overview-foot"><span><b>{metrics.active}</b> active journeys</span><span>Showing up to 100 recent journeys · Open a journey to inspect its full recorded history.</span></div>
        </section>

        <section className="pulse-workspace">
          <div className="journey-list-panel">
            <div className="pulse-list-heading"><div><p className="eyebrow">OPERATIONS FEED</p><h2>Journeys <span className="heading-count">{loading ? "—" : journeyCounts.all}</span></h2></div><button className="refresh-button" onClick={() => void loadJourneys()} aria-label="Refresh journeys">↻</button></div>
            <div className="journey-tabs" role="group" aria-label="Quick journey filters">
              <button className={!journeyKind ? "active" : ""} onClick={() => chooseQueue("")}>All <span>{journeyCounts.all}</span></button>
              <button className={journeyKind === "ATTENTION" ? "active attention-tab" : "attention-tab"} onClick={() => chooseQueue("ATTENTION")}>Attention <span>{journeyCounts.attention}</span></button>
              <button className={journeyKind === "ACTIVE" ? "active" : ""} onClick={() => chooseQueue("ACTIVE")}>Active <span>{journeyCounts.active}</span></button>
              <button className={journeyKind === "COMPLETED" ? "active" : ""} onClick={() => chooseQueue("COMPLETED")}>Completed <span>{journeyCounts.completed}</span></button>
            </div>
            <div className="journey-filters">
              <label className="search-field"><span aria-hidden="true">⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Find a journey or cart" /></label>
              <select value={status} onChange={(event) => { setJourneyKind(event.target.value); setAttentionOnly(false); setStatus(event.target.value); }} aria-label="Filter journeys by status">
                <option value="">All journeys</option><option>ACTIVE</option><option>COMPLETED</option><option>BLOCKED</option><option>FAILED</option><option>ABANDONED</option><option>EXPIRED</option>
              </select>
            </div>
            {attentionJourneys.length > 0 && !journeyKind && !search && <div className="attention-banner"><span className="attention-icon">!</span><div><strong>{attentionJourneys.length} {attentionJourneys.length === 1 ? "journey needs" : "journeys need"} attention</strong><small>Blocked check, failed action, or timed out merchant result.</small></div><button onClick={() => chooseQueue("ATTENTION")}>Review <span>→</span></button></div>}
            <div className="journey-list" aria-live="polite">
              {loading && <div className="pulse-empty">Loading this merchant’s journeys…</div>}
              {!loading && visibleJourneys.length === 0 && <div className="pulse-empty"><strong>{attentionOnly ? "No journeys need attention" : "No journeys found"}</strong><span>{attentionOnly ? "Blocked checks and failed outcomes will appear here." : "Try another merchant ID or clear the filters."}</span></div>}
              {!loading && visibleJourneys.map((journey) => (
                <button key={journey.journeyId} className={`journey-row ${selectedId === journey.journeyId ? "selected" : ""}`} onClick={() => selectJourney(journey.journeyId)}>
                  <span className={`journey-dot ${decisionTone(journey.status)} ${attentionJourneys.some((row) => row.journeyId === journey.journeyId) ? "attention-dot" : ""}`} />
                  <span className="journey-row-copy"><strong>{journey.journeyId}</strong><small>{journey.cartId || "No cart ID"}</small><span className="journey-row-event">{pretty(journey.latestEventType || "No events yet")}</span></span>
                  <span className="journey-row-state"><span className={`status-pill ${decisionTone(journey.status)}`}>{pretty(journey.status)}</span>{journey.latestDecision && <span className={`decision-mini ${decisionTone(journey.latestDecision)}`}>{pretty(journey.latestDecision)}</span>}<time>{dateLabel(journey.lastEventAt || journey.startedAt, { dateStyle: "short", timeStyle: "short" })}</time></span>
                </button>
              ))}
            </div>
            <div className="list-footer"><span>Showing {visibleJourneys.length} of up to {journeys.length} loaded</span><span>Newest first</span></div>
          </div>

          <aside className="journey-detail-panel">
            {!selectedId && <div className="pulse-empty detail-empty"><span className="empty-orbit">◎</span><strong>Your journey inspector</strong><span>Select a journey to trace its authorization checks and outcomes.</span></div>}
            {selectedId && (detailLoading || !detail) && <div className="pulse-empty detail-empty">Loading journey details…</div>}
            {detail && !detailLoading && <>
              <div className="inspector-kicker"><span className="inspector-mark">◎</span><div><p className="eyebrow">JOURNEY INSPECTOR</p><span>Recorded commerce activity</span></div><button className="copy-id" title="Copy journey ID" onClick={() => navigator.clipboard?.writeText(detail.journeyId).catch(() => {})}>Copy ID</button></div>
              <div className="detail-title-row"><h2>{detail.journeyId}</h2><span className={`status-pill ${decisionTone(detail.status)}`}>{pretty(detail.status)}</span></div>
              <div className="detail-subline"><span>Merchant <b>{detail.merchantId}</b></span><span>Cart <b>{detail.cartId || "—"}</b></span></div>
              {detail.explanation && <section className={`journey-explanation ${decisionTone(detail.explanation.outcome)}`} aria-label="Journey explanation">
                <div><p className="eyebrow">JOURNEY SUMMARY · {pretty(detail.explanation.outcome)}</p><button className="copy-id" title="Copy journey summary" onClick={() => navigator.clipboard?.writeText(`${detail.explanation.summary}\nCause: ${detail.explanation.cause}\nNext step: ${detail.explanation.nextStep}`).catch(() => {})}>Copy summary</button></div>
                <p>{detail.explanation.summary}</p>
                <dl><div><dt>Cause</dt><dd>{detail.explanation.cause}</dd></div><div><dt>Suggested next step</dt><dd>{detail.explanation.nextStep}</dd></div></dl>
              </section>}
              <div className="journey-stepper" aria-label="Journey progress">
                {["Authorization", "Agent check", "Merchant result", "Payment"].map((step, index) => {
                  const eventNames = (detail.timeline || []).map((event) => String(event.type || "").toUpperCase());
                  const hasStep = index === 0 ? eventNames.some((name) => name.includes("AUTHORIZATION") || name.includes("MANDATE"))
                    : index === 1 ? (detail.decisions || []).length > 0
                      : index === 2 ? eventNames.some((name) => name.includes("RESULT") || name.includes("CART"))
                        : eventNames.some((name) => name.includes("PAYMENT"));
                  const current = index === 1 && (detail.decisions || []).some((decision) => decision.decision === "BLOCK");
                  return <div className={`step ${hasStep ? "done" : ""} ${current ? "step-alert" : ""}`} key={step}><span>{hasStep ? (current ? "!" : "✓") : String(index + 1).padStart(2, "0")}</span><small>{step}</small></div>;
                })}
              </div>
              {(detail.decisions || []).some((decision) => decision.decision === "BLOCK") && <div className="exception-card"><div className="exception-heading"><span>!</span><div><strong>Authorization check blocked</strong><small>Review the saved rule finding before taking action.</small></div></div><p>{keyReason}</p></div>}
              <div className="detail-columns">
                <div className="detail-section timeline-section"><div className="detail-section-heading"><div><p className="eyebrow">WHAT HAPPENED</p><h3>Journey timeline</h3></div><span>{detail.timeline?.length || 0} events</span></div>
                  {!detail.timeline?.length && <p className="muted-copy">No activity has been saved for this journey yet.</p>}
                  <ol className="pulse-timeline">{(detail.timeline || []).map((event) => <li key={event.eventId}>
                    <span className={`timeline-marker ${decisionTone(event.outcome)}`} />
                    <div className="timeline-copy"><div><strong>{pretty(event.type)}</strong><time>{dateLabel(event.occurredAt, { dateStyle: "short", timeStyle: "short" })}</time></div><small>{pretty(event.source)}{event.outcome ? ` · ${pretty(event.outcome)}` : ""}</small>
                      {event.details && <details><summary>Inspect saved event data</summary><pre>{JSON.stringify(event.details, null, 2)}</pre></details>}
                    </div>
                  </li>)}</ol>
                </div>
                <div className="detail-section decision-section"><div className="detail-section-heading"><div><p className="eyebrow">WHY IT WAS ALLOWED OR BLOCKED</p><h3>Authorization checks</h3></div><span>{detail.decisions?.length || 0}</span></div>
                  {!detail.decisions?.length && <p className="muted-copy">No Guard decisions recorded for this journey.</p>}
                  {(detail.decisions || []).map((decision) => <details className="guard-decision" key={decision.decisionId}><summary><span className={`decision-chip ${decisionTone(decision.decision)}`}>{pretty(decision.decision)}</span><strong>{pretty(decision.action)}</strong><time>{dateLabel(decision.evaluatedAt, { dateStyle: "short", timeStyle: "short" })}</time></summary>
                    <p className="decision-id">Decision {decision.decisionId}</p>
                    {decision.ruleResults?.length > 0 ? <ul>{decision.ruleResults.map((rule, index) => <li key={`${rule.ruleId || "rule"}-${index}`}><b className={decisionTone(rule.result || rule.status)}>{pretty(rule.result || rule.status || "CHECK")}</b><span>{readableReason(rule.message || rule.reason || rule.ruleId)}</span></li>)}</ul>
                      : <p>{readableReason(decision.reasons)}</p>}
                    <details className="raw-context"><summary>View saved evaluation context</summary>{decision.context && <pre>{JSON.stringify(decision.context, null, 2)}</pre>}</details>
                  </details>)}
                  <div className="evidence-note"><span>ⓘ</span><p>Evidence and checks shown here reflect what this demo recorded. Authorization artifacts may be simulated and are not independently verified.</p></div>
                </div>
              </div>
              <div className="detail-meta"><span>Journey started {dateLabel(detail.startedAt)}</span><span>Last activity {dateLabel(detail.lastEventAt)}</span></div>
            </>}
          </aside>
        </section>
        <p className="pulse-footnote">Mandate Lens records agent decisions alongside merchant and payment outcomes, giving your team one place to inspect the full journey.</p>
      </main>
      </div>
    </div>
  );
}

export default PulseDashboard;
