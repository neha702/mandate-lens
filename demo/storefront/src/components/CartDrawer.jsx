function CartDrawer({ cart, onClose, onRemove }) {
  const total = cart.reduce(
    (sum, product) => sum + product.price,
    0
  );

  return (
    <div className="cart-overlay">
      <div className="cart-drawer">
        <div className="cart-header">
          <h2>Your Cart</h2>

          <button onClick={onClose}>✕</button>
        </div>

        {cart.length === 0 ? (
          <p>Your cart is empty.</p>
        ) : (
          <>
            {cart.map((product, index) => (
              <div className="cart-item" key={`${product.id}-${index}`}>
                <div>
                  <h3>{product.name}</h3>
                  <p>${product.price}</p>
                </div>

                <button onClick={() => onRemove(index)}>
                  Remove
                </button>
              </div>
            ))}

            <div className="cart-total">
              <span>Total</span>
              <strong>${total}</strong>
            </div>

            <button className="checkout-button">
              Proceed to Checkout
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default CartDrawer;