import { useState } from "react";
import Navbar from "./components/Navbar";
import Store from "./pages/Store";
import "./App.css";

function App() {
  const [cart, setCart] = useState([]);

  function addToCart(product) {
    setCart((currentCart) => [
      ...currentCart,
      product,
    ]);
  }

  return (
    <>
      <Navbar
        cartCount={cart.length}
        onCartClick={() => console.log(cart)}
      />

      <Store onAddToCart={addToCart} />
    </>
  );
}

export default App;