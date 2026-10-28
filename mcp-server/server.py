"""Local FastMCP shopping demo. Cart and policy data are intentionally in-memory."""

from __future__ import annotations

import base64
import json
import os
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlencode, urlparse
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastmcp import FastMCP, FastMCPApp
from prefab_ui.actions import SetState
from prefab_ui.actions.mcp import CallTool
from prefab_ui.app import PrefabApp
from prefab_ui.components import (
    Badge, Button, Card, CardContent, CardHeader, Column, ForEach,
    Grid, Heading, Image, Row, Separator, Small, Text,
)
from prefab_ui.rx import RESULT, Rx


MERCHANT_API_BASE_URL = os.getenv("MERCHANT_API_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
DEMO_BUDGET = float(os.getenv("MCP_DEMO_BUDGET", "600.00"))
PRODUCTS: list[dict] = []
CART: dict[str, int] = {}
IMAGE_CACHE: dict[str, str] = {}

shop = FastMCPApp("stridehub-shop")


def current_cart() -> dict:
    lines = []
    for product in PRODUCTS:
        quantity = CART.get(product["id"], 0)
        if quantity:
            lines.append({**product, "quantity": quantity, "line_total": round(product["price"] * quantity, 2)})
    total = round(sum(line["line_total"] for line in lines), 2)
    return {"items": lines, "total": total, "budget": DEMO_BUDGET, "currency": "USD"}


def fetch_footwear_products() -> list[dict]:
    """Fetch current footwear records from the merchant's product API."""
    request = Request(
        f"{MERCHANT_API_BASE_URL}/api/products",
        headers={"Accept": "application/json"},
    )
    try:
        with urlopen(request, timeout=5) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        raise RuntimeError(f"Merchant product API returned HTTP {error.code}.") from error
    except (URLError, TimeoutError, json.JSONDecodeError) as error:
        raise RuntimeError(
            f"Could not load products from {MERCHANT_API_BASE_URL}. Start the merchant API and try again."
        ) from error

    if not isinstance(payload, list):
        raise RuntimeError("Merchant product API returned an unexpected response.")

    products = [
        product for product in payload
        if isinstance(product, dict)
        and any(token in str(product.get("category", "")).upper()
                for token in ("SHOE", "FOOTWEAR", "SNEAKER", "BOOT", "SANDAL"))
    ]
    products.sort(key=lambda product: str(product.get("name", "")).casefold())
    return products


def shoe_art(color: str) -> str:
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="720" height="440" viewBox="0 0 720 440">
      <defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f4f5f0"/><stop offset="1" stop-color="#e1e9df"/></linearGradient></defs>
      <rect width="720" height="440" rx="24" fill="url(#bg)"/>
      <ellipse cx="365" cy="338" rx="230" ry="24" fill="#34443a" opacity=".12"/>
      <path d="M133 278c49-8 93-41 129-104 12-21 31-31 50-14l73 65c32 28 75 42 128 48l67 9c26 4 42 19 42 40 0 18-17 31-44 33l-416 2c-38 0-59-18-59-44 0-18 10-30 30-35z" fill="{color}"/>
      <path d="M128 320c95 11 225 7 357 2l109-4c23-1 38 8 38 21 0 15-16 23-42 25l-413 6c-51 1-83-10-83-28 0-13 12-21 34-22z" fill="#fff"/>
      <path d="M293 191l87 39m-110-3l98 42m-128-8l92 39" fill="none" stroke="#f8f8f4" stroke-width="13" stroke-linecap="round"/>
      <path d="M431 266l87 12" fill="none" stroke="#26392d" stroke-width="8" stroke-linecap="round" opacity=".6"/>
    </svg>'''
    encoded = base64.b64encode(svg.encode("utf-8")).decode("ascii")
    return f"data:image/svg+xml;base64,{encoded}"


def product_image_source(product: dict, index: int) -> str:
    """Inline catalog images for MCP App CSP; always return a renderable fallback."""
    image_url = product.get("imageUrl")
    if isinstance(image_url, str) and image_url not in IMAGE_CACHE:
        parsed = urlparse(image_url)
        if parsed.scheme == "https" and parsed.hostname == "images.unsplash.com":
            query = dict(item.split("=", 1) for item in parsed.query.split("&") if "=" in item)
            query.update({"fit": "crop", "w": "640", "q": "72", "fm": "jpg"})
            url = parsed._replace(query=urlencode(query)).geturl()
            request = Request(url, headers={"Accept": "image/jpeg", "User-Agent": "MandateLensDemo/1.0"})
            try:
                with urlopen(request, timeout=4) as response:
                    content_type = response.headers.get_content_type()
                    image_bytes = response.read(1_000_001)
                if content_type.startswith("image/") and len(image_bytes) <= 1_000_000:
                    encoded = base64.b64encode(image_bytes).decode("ascii")
                    IMAGE_CACHE[image_url] = f"data:{content_type};base64,{encoded}"
            except (HTTPError, URLError, TimeoutError, OSError):
                pass

    if isinstance(image_url, str) and image_url in IMAGE_CACHE:
        return IMAGE_CACHE[image_url]
    return shoe_art(("#db503f", "#bd3c36", "#c987a1")[index % 3])


def preload_product_images(products: list[dict]) -> list[str]:
    if not products:
        return []
    with ThreadPoolExecutor(max_workers=min(8, len(products))) as executor:
        return list(executor.map(
            lambda item: product_image_source(item[1], item[0]),
            enumerate(products),
        ))


@shop.tool()
def add_item_to_demo_cart(product_id: str) -> dict:
    """Add a footwear item to the local demo cart after fetching its current details from the merchant API.

    Use only as a follow-up to an explicit footwear shopping request. This only simulates the cart;
    it does not call the merchant cart API or AgentGuard.
    """
    global PRODUCTS
    try:
        PRODUCTS = fetch_footwear_products()
    except RuntimeError as error:
        return {"message": str(error), "ok": False, "cart": current_cart()}
    product = next((item for item in PRODUCTS if item.get("id") == product_id), None)
    if product is None:
        return {"message": "That product was not found.", "ok": False,
                "decision": "BLOCK", "cart": current_cart()}

    current_total = current_cart()["total"]
    if product.get("availability") != "IN_STOCK" or int(product.get("availableQuantity", 0)) < 1:
        return {"message": f"{product.get('name', 'This item')} is currently unavailable.",
                "ok": False, "decision": "BLOCK", "cart": current_cart()}
    proposed_total = round(current_total + float(product["price"]), 2)
    if proposed_total > DEMO_BUDGET:
        return {"message": f"Simulated check: BLOCK. The proposed total ${proposed_total:.2f} is above the ${DEMO_BUDGET:.2f} demo budget.",
                "ok": False, "decision": "BLOCK", "cart": current_cart()}

    CART[product_id] = CART.get(product_id, 0) + 1
    return {"message": f"Simulated check: ALLOW. {product['name']} was added. This is a local demo rule, not AgentGuard.",
            "ok": True, "decision": "ALLOW", "cart": current_cart()}


@shop.tool()
def reset_demo_cart() -> dict:
    """Clear the in-memory demo cart."""
    CART.clear()
    return {"message": "Demo cart cleared.", "ok": True, "cart": current_cart()}


@shop.tool()
def get_footwear_products(query: str = "") -> dict:
    """Look up footwear in the live merchant catalog.

    Call this tool only when the user asks to browse, list, compare, or find footwear such as
    running shoes, sneakers, boots, or sandals. Do not call for general shopping questions,
    checkout help, payment questions, or unrelated product categories.
    """
    global PRODUCTS
    try:
        PRODUCTS = fetch_footwear_products()
    except RuntimeError as error:
        return {"ok": False, "source": "merchant product API", "message": str(error), "products": []}
    terms = [term.casefold() for term in query.split() if term.strip()]
    matched = [product for product in PRODUCTS if all(
        term in " ".join(str(product.get(key, "")) for key in ("name", "description", "category")).casefold()
        for term in terms
    )]
    return {"ok": True, "source": "merchant product API", "count": len(matched), "products": matched}


@shop.ui()
def open_shoe_shop() -> PrefabApp:
    """Open the live footwear shop only when the user asks to browse or shop for footwear."""
    global PRODUCTS
    try:
        PRODUCTS = fetch_footwear_products()
    except RuntimeError as error:
        with Column(gap=3, css_class="p-5") as view:
            Heading("Footwear catalog unavailable", level=1)
            Text(str(error))
        return PrefabApp(view=view, state={"result": {"ok": False, "message": str(error)}})
    image_sources = preload_product_images(PRODUCTS)

    initial_result = {
        "message": "Products loaded live from the merchant catalog API. Cart and budget checks are simulated locally.",
        "ok": True,
        "cart": current_cart(),
    }
    with Column(gap=5, css_class="p-5") as view:
        with Row(justify="between", align="center"):
            with Column(gap=1):
                Small("AI SHOPPING · LOCAL DEMO", bold=True, css_class="tracking-widest text-muted-foreground")
                Heading("StrideHub running shoes", level=1)
            Badge("SIMULATED · NO PAYMENT", variant="warning")

        with Card():
            with CardContent():
                with Row(justify="between", align="center"):
                    with Column(gap=1):
                        Small("SIMULATED SHOPPER AUTHORIZATION", bold=True, css_class="text-muted-foreground")
                        Text("Product details come from the merchant API. This sample cart budget is local demo data.")
                    Badge(f"${DEMO_BUDGET:.2f} USD", variant="success")

        with Grid(columns=[1, 1, 1], gap=4):
            for index, product in enumerate(PRODUCTS):
                image_url = image_sources[index]
                currency = product.get("currency", "USD")
                price = float(product.get("price", 0))
                with Card():
                    Image(src=image_url, alt=f"{product['name']} footwear product image",
                          width="100%", height="150px")
                    with CardHeader():
                        Small(str(product.get("category", "Footwear")).replace("_", " ").upper(), bold=True,
                              css_class="text-muted-foreground")
                        Heading(str(product.get("name", "Footwear")), level=3)
                    with CardContent():
                        with Column(gap=3):
                            Text(str(product.get("description", "")))
                            with Row(justify="between", align="center"):
                                Text(f"{price:.2f} {currency}", css_class="font-semibold")
                                Button("Add to cart", variant="default", onClick=CallTool(
                                    add_item_to_demo_cart,
                                    arguments={"product_id": product["id"]},
                                    onSuccess=SetState("result", RESULT),
                                ))

        with Card():
            with CardHeader():
                with Row(justify="between", align="center"):
                    Heading("Your cart", level=2)
                    Button("Clear cart", variant="outline", onClick=CallTool(
                        reset_demo_cart,
                        onSuccess=SetState("result", RESULT),
                    ))
            with CardContent():
                with ForEach("result.cart.items") as line:
                    with Row(justify="between", css_class="border-b py-2"):
                        Text(line.name)
                        Text(line.quantity)
                        Text(line.line_total)
                Separator()
                with Row(justify="between", align="center"):
                    Text("Cart total", css_class="font-semibold")
                    Heading(Rx("result.cart.total"), level=3)
                    Text(Rx("result.cart.currency"))
                Text(Rx("result.message"), css_class="text-sm text-muted-foreground")

        Text("Live product data is read from the merchant API. Cart, budget, and allow/block checks remain local demo behavior; this does not yet call the merchant cart API, Mandate Lens, or AgentGuard.",
             css_class="text-xs text-muted-foreground")
    return PrefabApp(view=view, state={"result": initial_result})


mcp = FastMCP("Mandate Lens Merchant Demo", providers=[shop])

if __name__ == "__main__":
    # Optional stdio mode; the documented demo flow uses local Streamable HTTP.
    mcp.run()
