package com.emeralds.mandatelens.agentguard.service;

import com.emeralds.mandatelens.agentguard.model.GuardCart;
import com.emeralds.mandatelens.agentguard.model.GuardEvaluationContext;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;
import com.emeralds.mandatelens.merchant.catalog.model.Product;
import com.emeralds.mandatelens.merchant.catalog.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Effect.BLOCK;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Effect.NONE;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Result.FAIL;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Result.PASS;

/** Verifies submitted item IDs, categories and prices against merchant-owned catalog records. */
@Component
public class CatalogIntegrityRuleEvaluator implements RuleEvaluator {
    private final ProductService products;

    public CatalogIntegrityRuleEvaluator(ProductService products) { this.products = products; }

    @Override public String evaluatorType() { return "DETERMINISTIC"; }

    @Override
    public List<RuleFinding> evaluate(GuardEvaluationContext context) {
        GuardCart cart = context.proposedCart();
        if (cart == null) return List.of();
        List<Product> catalog = products.getProducts();
        return cart.items().stream().map(item -> {
            Product product = catalog.stream().filter(candidate -> candidate.getId().equals(item.productId()))
                    .findFirst().orElse(null);
            if (product == null) {
                return finding(RuleFinding.Result.FAIL, RuleFinding.Effect.BLOCK,
                        "PRODUCT_NOT_FOUND", "A cart product does not exist in the merchant catalog.",
                        Map.of("productId", item.productId()));
            }
            boolean categoryMatches = product.getCategory().equalsIgnoreCase(item.category());
            boolean priceMatches = product.getPrice().compareTo(item.unitPrice()) == 0;
            boolean currencyMatches = product.getCurrency().equalsIgnoreCase(cart.currency());
            if (!categoryMatches || !priceMatches || !currencyMatches) {
                return finding(FAIL, BLOCK, "UNTRUSTED_PRODUCT_CONTEXT",
                        "Cart product attributes do not match the merchant catalog.",
                        Map.of("productId", item.productId(), "catalogCategory", product.getCategory(),
                                "observedCategory", item.category(), "catalogUnitPrice", product.getPrice(),
                                "observedUnitPrice", item.unitPrice(), "catalogCurrency", product.getCurrency(),
                                "cartCurrency", cart.currency()));
            }
            return finding(PASS, NONE, "PRODUCT_CONTEXT_VERIFIED",
                    "Cart product attributes match the merchant catalog.",
                    Map.of("productId", item.productId()));
        }).toList();
    }

    private RuleFinding finding(RuleFinding.Result result, RuleFinding.Effect effect,
                                String code, String message, Map<String, Object> evidence) {
        return new RuleFinding("PRODUCT_IDENTITY_AND_ATTRIBUTES_TRUSTED", "CART", evaluatorType(),
                result, effect, code, message, evidence, null, Map.of());
    }
}
