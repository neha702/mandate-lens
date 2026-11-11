function ProductCard({ product, onAddToCart, busy, disabled, index }) {
  const currency = product.currency || "USD";
  const available = product.availability === "IN_STOCK" && product.availableQuantity > 0;
  const price = new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(product.price));

  return (
    <article className="product-card">
      <div className={`product-image-container product-tone-${index % 3}`}>
        {product.imageUrl ? <img src={product.imageUrl} alt={product.name} className="product-image" loading="lazy" /> : <div className="product-image-fallback">PACEPOINT</div>}
        <span className="product-badge">{product.category?.replaceAll("_", " ") || "RUNNING"}</span>
        <button className="quick-add" onClick={() => onAddToCart(product)} disabled={!available || busy || disabled} aria-label={`Add ${product.name} to bag`}>
          {busy ? "…" : "+"}
        </button>
      </div>
      <div className="product-info">
        <div className="product-heading"><div><span className="product-category">PACEPOINT / RUN</span><h3>{product.name}</h3></div><span className="price">{price}</span></div>
        <p className="product-description">{product.description}</p>
        <div className="product-bottom">
          <span className={`stock-state ${available ? "in-stock" : "out-of-stock"}`}><i />{available ? `${product.availableQuantity} available` : "Currently unavailable"}</span>
          <button className="add-button" onClick={() => onAddToCart(product)} disabled={!available || busy || disabled}>{busy ? "Adding…" : disabled ? "Loading cart…" : "Add to bag"}<span aria-hidden="true">↗</span></button>
        </div>
      </div>
    </article>
  );
}

export default ProductCard;
