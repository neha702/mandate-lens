import { useMemo, useState } from "react";
import ProductCard from "../components/ProductCard";

function Store({ products, loading, error, onAddToCart, busyProduct, cartReady }) {
  const [sort, setSort] = useState("featured");
  const shownProducts = useMemo(() => {
    const sorted = [...products];
    if (sort === "price-low") sorted.sort((a, b) => Number(a.price) - Number(b.price));
    if (sort === "price-high") sorted.sort((a, b) => Number(b.price) - Number(a.price));
    return sorted;
  }, [products, sort]);
  const heroProduct = products[0];

  return (
    <>
      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow hero-eyebrow">PACEPOINT RUNNING · SPRING / SUMMER</p>
          <h1>Find your<br />next <em>stride.</em></h1>
          <p>Purpose-built running shoes for the roads, trails, and everything that gets you out the door.</p>
          <a className="hero-button" href="#running">Shop the collection <span aria-hidden="true">↗</span></a>
          <div className="hero-proof"><span>01 / 03</span><i /><span>Engineered for everyday miles</span></div>
        </div>
        <div className="hero-art" aria-label="Featured PacePoint running shoe">
          {heroProduct?.imageUrl ? <img src={heroProduct.imageUrl} alt={heroProduct.name} /> : <div className="hero-placeholder">PACEPOINT</div>}
          <div className="hero-product-label"><span>THE EVERYDAY RUNNER</span><b>01</b></div>
          <span className="hero-vertical">PACEPOINT PERFORMANCE SERIES</span>
        </div>
      </section>

      <section className="products-section" id="running">
        <div className="section-header">
          <div className="section-title"><p className="eyebrow">BUILT TO GO FURTHER</p><h2>Running shoes</h2><p>Comfort and performance, one mile at a time.</p></div>
          <label className="sort-control">Sort by <select value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="featured">Featured</option>
            <option value="price-low">Price: low to high</option>
            <option value="price-high">Price: high to low</option>
          </select></label>
        </div>

        <div className="collection-meta"><span>{loading ? "Loading collection…" : `${shownProducts.length} styles`}</span><span>Free shipping on orders over $100</span></div>
        <div className="product-grid">
          {loading && <div className="catalog-state">Loading the latest from the merchant catalog…</div>}
          {!loading && error && <div className="catalog-state catalog-error">The catalog could not be loaded. {error}</div>}
          {!loading && !error && shownProducts.map((product, index) => (
            <ProductCard key={product.id} product={product} onAddToCart={onAddToCart} busy={busyProduct === product.id} disabled={!cartReady} index={index} />
          ))}
          {!loading && !error && shownProducts.length === 0 && <div className="catalog-state">No products are available right now.</div>}
        </div>
      </section>

      <section className="story-strip" id="our-story">
        <span className="story-mark">P.</span><div><p className="eyebrow">MADE FOR THE LONG RUN</p><h2>Good gear gets you out there.</h2></div><p>Thoughtful design, dependable comfort, and no distractions between you and the next mile.</p>
      </section>
      <div id="help" className="shipping-note"><span>Need a hand?</span><span>Our local demo checkout uses PayPal Sandbox. Purchases use test money only.</span></div>
    </>
  );
}

export default Store;
