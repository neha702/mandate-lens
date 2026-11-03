async function request(path) {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.detail || body?.message || body?.error || `Mandate Lens returned HTTP ${response.status}.`);
  }
  return body;
}

export function getMerchantJourneys(merchantId, { status = "", search = "" } = {}) {
  const query = new URLSearchParams({ merchantId });
  if (status) query.set("status", status);
  if (search.trim()) query.set("search", search.trim());
  return request(`/api/v1/journeys?${query.toString()}`);
}

export function getMerchantJourney(journeyId, merchantId) {
  const query = new URLSearchParams({ merchantId });
  return request(`/api/v1/journeys/${encodeURIComponent(journeyId)}?${query.toString()}`);
}
