const API_URL = import.meta.env.VITE_API_URL || "";

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message = body?.detail || body?.message || body?.error || `Merchant API returned HTTP ${response.status}.`;
    const error = new Error(message);
    error.status = response.status;
    error.response = body;
    throw error;
  }
  return body;
}

export function getProducts() {
  return request("/api/products");
}

export function createCart(cart) {
  return request("/merchant-cart", { method: "POST", body: JSON.stringify(cart) });
}

export function getCart(cartId) {
  return request(`/merchant-cart/${encodeURIComponent(cartId)}`);
}

export function replaceCart(cartId, cart) {
  return request(`/merchant-cart/${encodeURIComponent(cartId)}`, {
    method: "PUT",
    body: JSON.stringify(cart),
  });
}

export function checkoutCart(cartId, checkout) {
  return request(`/merchant-cart/${encodeURIComponent(cartId)}/checkout`, {
    method: "POST",
    body: JSON.stringify(checkout),
  });
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
