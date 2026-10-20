function ProductDetails({ product, onBack, onAddToCart }) {
  if (!product) {
    return null;
  }

  return (
    <main className="product-details">
      <button className="back-button" onClick={onBack}>
        ← Back to Running Shoes
      </button>

      <div className="product-details-content">
        <div className="product-details-image">
          <img
            src={product.imageUrl}
            alt={product.name}
          />
        </div>

        <div className="product-details-info">
          <span className="product-category">
            {product.category}
          </span>

          <h1>{product.name}</h1>

          <p className="details-price">
            ${product.price} {product.currency}
          </p>

          <p>{product.description}</p>

          <div className="size-selector">
            <p>Size</p>

            <button>8</button>
            <button>9</button>
            <button>10</button>
            <button>11</button>
          </div>

          <button
            className="add-details-button"
            onClick={() => onAddToCart(product)}
          >
            Add to Cart
          </button>
        </div>
      </div>
    </main>
  );
}

export default ProductDetails;