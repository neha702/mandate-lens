function CartDrawer({ open, cart, shipping, busy, error, onClose, onShippingChange, onQuantityChange, onClearCart, onCheckout }) {
  if (!open) return null;
  const items = cart?.items || [];
  const currency = cart?.totals?.total?.currency_code || "USD";
  const total = Number(cart?.totals?.total?.value || 0);
  const isDemoPayment = cart?.payment_method?.type === "demo";
  const money = (value) => new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(value || 0));

  return (
    <div className="cart-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside className="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-title">
        <div className="cart-header">
          <div><p className="eyebrow">PACEPOINT ORDER</p><h2 id="cart-title">Your bag <span>{items.length ? `(${items.reduce((sum, item) => sum + item.quantity, 0)})` : ""}</span></h2></div>
          <button className="drawer-close" onClick={onClose} aria-label="Close cart">×</button>
        </div>

        {error && <div className="drawer-error" role="alert"><b>We couldn’t update your cart.</b><span>{error}</span></div>}

        {items.length === 0 ? (
          <div className="empty-bag"><span className="empty-bag-mark">P.</span><h3>Your next run starts here.</h3><p>Your bag is empty. Explore the collection and find your next stride.</p><button className="button button-dark" onClick={onClose}>Keep shopping</button></div>
        ) : (
          <>
            <div className="cart-items">
              {items.map((item) => (
                <article className="cart-item" key={item.variant_id}>
                  <div className="cart-item-art"><span>PACE</span></div>
                  <div className="cart-item-copy"><p className="eyebrow">{item.variant_id}</p><h3>{item.name}</h3><span>{money(item.unit_amount?.value)} each</span>
                    <div className="quantity-control" aria-label={`Quantity for ${item.name}`}><button disabled={busy} onClick={() => onQuantityChange(item.variant_id, item.quantity - 1)} aria-label="Decrease quantity">−</button><span>{item.quantity}</span><button disabled={busy} onClick={() => onQuantityChange(item.variant_id, item.quantity + 1)} aria-label="Increase quantity">+</button></div>
                  </div>
                  <div className="cart-item-price"><b>{money(item.item_total?.value)}</b><button disabled={busy} onClick={() => onQuantityChange(item.variant_id, 0)}>Remove</button></div>
                </article>
              ))}
            </div>

            <div className="shipping-form">
              <div className="drawer-section-heading"><div><p className="eyebrow">DELIVERY DETAILS</p><h3>Where should we send it?</h3></div><span>Demo details are editable</span></div>
              <div className="form-grid two-columns">
                <label>First name<input value={shipping.givenName} onChange={(event) => onShippingChange("givenName", event.target.value)} autoComplete="given-name" /></label>
                <label>Last name<input value={shipping.surname} onChange={(event) => onShippingChange("surname", event.target.value)} autoComplete="family-name" /></label>
              </div>
              <label>Email address<input type="email" value={shipping.email} onChange={(event) => onShippingChange("email", event.target.value)} autoComplete="email" /></label>
              <label>Street address<input value={shipping.addressLine1} onChange={(event) => onShippingChange("addressLine1", event.target.value)} autoComplete="address-line1" /></label>
              <div className="form-grid two-columns">
                <label>City<input value={shipping.city} onChange={(event) => onShippingChange("city", event.target.value)} autoComplete="address-level2" /></label>
                <label>State<input value={shipping.state} onChange={(event) => onShippingChange("state", event.target.value)} autoComplete="address-level1" /></label>
                <label>ZIP code<input value={shipping.postalCode} onChange={(event) => onShippingChange("postalCode", event.target.value)} autoComplete="postal-code" /></label>
                <label>Country<select value={shipping.countryCode} onChange={(event) => onShippingChange("countryCode", event.target.value)}><option value="US">United States</option></select></label>
              </div>
              <p className="shipping-hint">This demo authorization currently allows shipping to the United States.</p>
            </div>

            <div className="cart-summary">
              <button className="clear-cart-button" disabled={busy} onClick={onClearCart}>Clear bag</button>
              <div className="summary-line"><span>Subtotal</span><span>{money(cart?.totals?.subtotal?.value || total)}</span></div>
              <div className="summary-line"><span>Shipping</span><span>{Number(cart?.totals?.shipping?.value || 0) === 0 ? "Complimentary" : money(cart.totals.shipping.value)}</span></div>
              <div className="summary-total"><span>Total</span><strong>{money(total)}</strong></div>
              <button className="paypal-checkout" onClick={onCheckout} disabled={busy || !items.length}>{busy ? <><i className="button-spinner" /> Updating securely…</> : <>{isDemoPayment ? "Complete demo checkout" : "Continue with PayPal Sandbox"} <span>↗</span></>}</button>
              <div className="sandbox-note"><span className="sandbox-shield">✓</span><p>{isDemoPayment
                ? <><b>Local simulated payment</b><br />PayPal Sandbox is disabled. Checkout is recorded as a demo; no PayPal request or money movement occurs.</>
                : <><b>PayPal Sandbox checkout</b><br />Uses test accounts and fake money. No real payment is taken.</>}</p></div>
              <p className="guard-note">Cart changes and checkout are checked by Mandate Lens before the merchant proceeds.</p>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}

export default CartDrawer;
