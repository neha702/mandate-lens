import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Navbar from "./components/Navbar";
import CartDrawer from "./components/CartDrawer";
import Store from "./pages/Store";
import PulseDashboard from "./pages/PulseDashboard";
import {
  checkoutCart,
  createCart,
  getCart,
  getProducts,
  replaceCart,
} from "./services/ProductService";
import "./App.css";

const CART_STORAGE_KEY = "pacepoint.merchant.cartId";
const EMPTY_SHIPPING = {
  givenName: "Demo",
  surname: "Shopper",
  email: "buyer@example.com",
  addressLine1: "1 Main St",
  addressLine2: "",
  city: "San Jose",
  state: "CA",
  postalCode: "95131",
  countryCode: "US",
};

function requestBody(items, shipping) {
  return {
    items: items.map((item) => ({
      variant_id: item.variant_id || item.id,
      quantity: item.quantity,
    })),
    customer: {
      name: { given_name: shipping.givenName, surname: shipping.surname },
      email_address: shipping.email,
    },
    shipping_address: {
      address_line_1: shipping.addressLine1,
      address_line_2: shipping.addressLine2 || undefined,
      admin_area_2: shipping.city,
      admin_area_1: shipping.state,
      postal_code: shipping.postalCode,
      country_code: shipping.countryCode,
    },
    payment_method: { type: "paypal" },
  };
}

function StorefrontApp() {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState(null);
  const [shipping, setShipping] = useState(EMPTY_SHIPPING);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [cartReady, setCartReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [busyProduct, setBusyProduct] = useState("");
  const [cartOpen, setCartOpen] = useState(false);
  const [error, setError] = useState("");
  const [catalogError, setCatalogError] = useState("");
  const [notice, setNotice] = useState("");
  const [lastOrder, setLastOrder] = useState(null);
  const handledReturn = useRef(false);

  const cartItems = cart?.items || [];
  const cartCount = useMemo(
    () => cartItems.reduce((sum, item) => sum + item.quantity, 0),
    [cartItems],
  );

  const acceptCart = useCallback((nextCart) => {
    if (nextCart?.status === "COMPLETED") {
      setLastOrder(nextCart);
      setCart(null);
      localStorage.removeItem(CART_STORAGE_KEY);
      return;
    }
    setCart(nextCart);
    if (nextCart?.id) localStorage.setItem(CART_STORAGE_KEY, nextCart.id);
    if (nextCart?.shipping_address || nextCart?.customer) {
      const address = nextCart.shipping_address || {};
      const customer = nextCart.customer || {};
      setShipping((current) => ({
        ...current,
        givenName: customer.name?.given_name || current.givenName,
        surname: customer.name?.surname || current.surname,
        email: customer.email_address || current.email,
        addressLine1: address.address_line_1 || current.addressLine1,
        addressLine2: address.address_line_2 || "",
        city: address.admin_area_2 || current.city,
        state: address.admin_area_1 || current.state,
        postalCode: address.postal_code || current.postalCode,
        countryCode: address.country_code || current.countryCode,
      }));
    }
  }, []);

  const loadProducts = useCallback(async () => {
    setLoadingProducts(true);
    setCatalogError("");
    try {
      setProducts(await getProducts());
    } catch (loadError) {
      setCatalogError(loadError.message);
    } finally {
      setLoadingProducts(false);
    }
  }, []);

  useEffect(() => {
    void loadProducts();

    async function restoreCartOrFinishPayPal() {
      const query = new URLSearchParams(window.location.search);
      const paymentState = query.get("payment")
        || (window.location.pathname.endsWith("/checkout/cancel") ? "cancel" : null)
        || (window.location.pathname.endsWith("/checkout/return") ? "return" : null);
      const cartId = query.get("cartId") || localStorage.getItem(CART_STORAGE_KEY);
      const orderToken = query.get("token");

      try {
        if (paymentState === "cancel") {
          if (cartId) {
            const savedCart = await getCart(cartId);
            acceptCart(savedCart);
          }
          setNotice("Payment was cancelled. Your cart is saved and no payment was captured.");
          window.history.replaceState({}, "", "/");
          return;
        }

        if ((paymentState === "return" || orderToken) && cartId) {
          if (handledReturn.current) return;
          handledReturn.current = true;
          if (!orderToken) throw new Error("PayPal returned without an order token. Your cart is still saved.");

          const savedCart = await getCart(cartId);
          if (savedCart.payment_method?.token !== orderToken) {
            throw new Error("The PayPal order does not match the saved cart.");
          }
          const isDemoPayment = savedCart.payment_method?.type === "demo";
          const completed = await checkoutCart(cartId, {
            payment_method: {
              type: isDemoPayment ? "demo" : "paypal",
              token: orderToken,
              payer_id: query.get("PayerID") || query.get("payerId") || undefined,
            },
          });
          setLastOrder(completed);
          setCart(null);
          setCartOpen(false);
          setNotice(isDemoPayment
            ? "Demo payment completed locally. No PayPal transaction or money movement occurred."
            : `Sandbox payment completed. Order ${completed.payment_confirmation?.merchant_order_number || orderToken} was captured.`);
          localStorage.removeItem(CART_STORAGE_KEY);
          window.history.replaceState({}, "", "/");
          return;
        }

        if (cartId) {
          const savedCart = await getCart(cartId);
          acceptCart(savedCart);
        }
      } catch (restoreError) {
        setError(restoreError.message);
        if (paymentState === "return" || paymentState === "cancel" || orderToken) {
          window.history.replaceState({}, "", "/");
        }
      } finally {
        setCartReady(true);
      }
    }

    void restoreCartOrFinishPayPal();
  }, [acceptCart, loadProducts]);

  const updateCart = useCallback(async (items, successMessage) => {
    if (!cart?.id) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await replaceCart(cart.id, requestBody(items, shipping));
      acceptCart(updated);
      if (successMessage) setNotice(successMessage);
    } catch (updateError) {
      setError(updateError.message);
    } finally {
      setBusy(false);
    }
  }, [acceptCart, cart?.id, shipping]);

  async function addToCart(product) {
    if (busy) return;
    setBusy(true);
    setBusyProduct(product.id);
    setError("");
    setNotice("");
    const current = cart?.status === "COMPLETED" ? [] : cartItems;
    const existing = current.find((item) => item.variant_id === product.id);
    const proposed = existing
      ? current.map((item) => item.variant_id === product.id ? { ...item, quantity: item.quantity + 1 } : item)
      : [...current, { variant_id: product.id, quantity: 1 }];
    try {
      const result = cart?.id && cart.status !== "COMPLETED"
        ? await replaceCart(cart.id, requestBody(proposed, shipping))
        : await createCart(requestBody(proposed, shipping));
      acceptCart(result);
      setCartOpen(true);
      setNotice(`${product.name} was sent to the merchant cart. Mandate Lens checked the cart change.`);
    } catch (addError) {
      setError(addError.message);
    } finally {
      setBusy(false);
      setBusyProduct("");
    }
  }

  function changeQuantity(variantId, quantity) {
    const proposed = quantity < 1
      ? cartItems.filter((item) => item.variant_id !== variantId)
      : cartItems.map((item) => item.variant_id === variantId ? { ...item, quantity } : item);
    void updateCart(proposed, quantity < 1 ? "Item removed from your cart." : "Cart quantity updated and checked.");
  }

  function updateShipping(field, value) {
    setShipping((current) => ({ ...current, [field]: value }));
  }

  async function continueToPayPal() {
    if (!cart?.id || cartItems.length === 0) return;
    const required = [shipping.givenName, shipping.surname, shipping.email, shipping.addressLine1,
      shipping.city, shipping.state, shipping.postalCode, shipping.countryCode];
    if (required.some((value) => !String(value || "").trim())) {
      setError("Complete the shipping and contact details before continuing to PayPal.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await replaceCart(cart.id, requestBody(cartItems, shipping));
      acceptCart(updated);
      if (updated.validation_status !== "VALID") {
        const issue = updated.validation_issues?.map((item) => item.user_message || item.message).filter(Boolean).join(" ");
        throw new Error(issue || "The merchant could not validate this cart.");
      }
      if (updated.payment_method?.type === "demo") {
        const completed = await checkoutCart(cart.id, {
          payment_method: { type: "demo", token: updated.payment_method.token },
        });
        acceptCart(completed);
        setLastOrder(completed);
        setCart(null);
        setCartOpen(false);
        setNotice("Demo payment completed locally. No PayPal transaction or money movement occurred.");
        localStorage.removeItem(CART_STORAGE_KEY);
        setBusy(false);
        return;
      }
      const approvalUrl = updated.payment_method?.approval_url;
      if (updated.payment_method?.type !== "paypal" || !approvalUrl) {
        throw new Error("The cart has no PayPal approval link. Check the Sandbox credentials and PAYPAL_SANDBOX_ENABLED flag in the backend .env file.");
      }
      window.location.assign(approvalUrl);
    } catch (checkoutError) {
      setError(checkoutError.message);
      setBusy(false);
    }
  }

  return (
    <div className="store-app">
      <Navbar cartCount={cartCount} onCartClick={() => setCartOpen(true)} />
      <main>
        {notice && <div className="status-banner success-banner" role="status"><span>✓</span>{notice}<button onClick={() => setNotice("")} aria-label="Dismiss message">×</button></div>}
        {error && <div className="status-banner error-banner" role="alert"><span>!</span>{error}<button onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}
        {lastOrder && <section className="order-confirmation"><span className="confirmation-icon">✓</span><div><p className="eyebrow">{lastOrder.payment_method?.type === "demo" ? "LOCAL DEMO ORDER COMPLETE" : "PAYPAL SANDBOX ORDER COMPLETE"}</p><h2>Thanks for your order.</h2><p>{lastOrder.payment_method?.type === "demo" ? "The payment was simulated locally. No PayPal transaction or money movement occurred." : "Captured in the PayPal test environment. No real money was moved."}</p></div><button className="button button-dark" onClick={() => { setLastOrder(null); setNotice(""); }}>Continue shopping</button></section>}
        <Store products={products} loading={loadingProducts} error={catalogError} onAddToCart={addToCart} busyProduct={busyProduct} cartReady={cartReady} />
      </main>
      <footer className="site-footer"><span>PACEPOINT</span><span>Built for miles that matter.</span><span>{cart?.payment_method?.type === "demo" ? "Local simulated checkout" : "PayPal Sandbox checkout"}</span></footer>
      <CartDrawer
        open={cartOpen}
        cart={cart}
        shipping={shipping}
        busy={busy}
        error={error}
        onClose={() => setCartOpen(false)}
        onShippingChange={updateShipping}
        onQuantityChange={changeQuantity}
        onClearCart={() => void updateCart([], "Your cart is now empty.")}
        onCheckout={continueToPayPal}
      />
    </div>
  );
}

function App() {
  return window.location.pathname.replace(/\/$/, "") === "/pulse"
    ? <PulseDashboard />
    : <StorefrontApp />;
}

export default App;
