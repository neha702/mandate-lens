export async function getProducts() {
  const response = await fetch("/api/products");

  if (!response.ok) {
    throw new Error(`Failed to load products (${response.status})`);
  }

  return response.json();
}