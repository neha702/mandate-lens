import { useEffect, useState } from "react";
import { getProducts } from "../services/productService";
import ProductCard from "../components/ProductCard";

function Store({ onAddToCart }) {
  const [products, setProducts] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    getProducts()
      .then(setProducts)
      .catch((loadError) => setError(loadError.message));
  }, []);

  return (
    <main>
      <section className="hero">
        <div>
          <p className="eyebrow">PACEPOINT RUNNING</p>

          <h1>
            Run further.
            <br />
            Run smarter.
          </h1>

          <p>
            Performance running shoes built for every mile.
          </p>

          <button className="hero-button">
            Shop Running Shoes
          </button>
        </div>
      </section>

      <section className="products-section" id="running">
        <div className="section-header">
          <div>
            <p className="eyebrow">COLLECTION</p>
            <h2>Running Shoes</h2>
          </div>

          <select>
            <option>Featured</option>
            <option>Price: Low to High</option>
            <option>Price: High to Low</option>
          </select>
        </div>

        <div className="product-grid">
          {error ? (
            <p role="alert">Could not load products: {error}</p>
          ) : (
            products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                onAddToCart={onAddToCart}
              />
            ))
          )}
        </div>
      </section>
    </main>
  );
}

export default Store;