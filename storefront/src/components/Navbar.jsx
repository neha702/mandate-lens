function Navbar({ cartCount, onCartClick }) {
  return (
    <nav className="navbar">
      <div className="logo">
        PACE<span>POINT</span>
      </div>

      <div className="nav-links">
        <a href="#running">Running Shoes</a>
        <a href="#apparel">Apparel</a>
        <a href="#accessories">Accessories</a>
      </div>

      <button className="cart-button" onClick={onCartClick}>
        🛒 Cart ({cartCount})
      </button>
    </nav>
  );
}

export default Navbar;