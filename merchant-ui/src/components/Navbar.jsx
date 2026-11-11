const PULSE_DASHBOARD_URL = import.meta.env.VITE_PULSE_DASHBOARD_URL || "http://localhost:5174";

function Navbar({ cartCount, onCartClick }) {
  return (
    <header className="navbar">
      <a className="brand" href="#top" aria-label="PacePoint home">
        <span className="brand-symbol">P</span>
        <span className="brand-name">PACE<span>POINT</span><small>RUNNING CO.</small></span>
      </a>
      <nav className="nav-links" aria-label="Main navigation">
        <a href="#running">Running</a>
        <a href="#our-story">Our story</a>
        <a href="#help">Help</a>
        <a href={PULSE_DASHBOARD_URL} target="_blank" rel="noreferrer">Pulse dashboard</a>
      </nav>
      <button className="cart-button" onClick={onCartClick} aria-label={`Open cart with ${cartCount} items`}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h8.7a2 2 0 0 0 1.9-1.4L22 8H6"/><circle cx="10" cy="20" r="1"/><circle cx="18" cy="20" r="1"/></svg>
        <span>Bag</span><b>{cartCount}</b>
      </button>
    </header>
  );
}

export default Navbar;
