function ProductCard({ product, onAddToCart }) {
  return (
    <div className="product-card">
      <div className="product-image-container">
        <img
          src={product.imageUrl}
          alt={product.name}
          className="product-image"
        />
      </div>

      <div className="product-info">
        <span className="product-category">
          Running Shoes
        </span>

        <h3>{product.name}</h3>

        <p>{product.description}</p>

        <div className="product-bottom">
          <span className="price">
            ${product.price}
          </span>

          <button onClick={() => onAddToCart(product)}>
            Add to Cart
          </button>
        </div>
      </div>
    </div>
  );
}

export default ProductCard;