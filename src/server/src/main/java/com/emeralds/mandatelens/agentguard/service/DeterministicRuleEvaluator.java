package com.emeralds.mandatelens.agentguard.service;

import com.emeralds.mandatelens.agentguard.model.GuardCart;
import com.emeralds.mandatelens.agentguard.model.GuardEvaluationContext;
import com.emeralds.mandatelens.agentguard.model.RuleFinding;
import com.emeralds.mandatelens.mandate.model.Mandate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Effect.BLOCK;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Effect.NONE;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Result.FAIL;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Result.NOT_APPLICABLE;
import static com.emeralds.mandatelens.agentguard.model.RuleFinding.Result.PASS;

@Component
public class DeterministicRuleEvaluator implements RuleEvaluator {
    private final boolean allowSimulatedMandates;

    public DeterministicRuleEvaluator(
            @Value("${agentguard.local.allow-simulated-mandates:false}") boolean allowSimulatedMandates) {
        this.allowSimulatedMandates = allowSimulatedMandates;
    }

    @Override public String evaluatorType() { return "DETERMINISTIC"; }

    @Override
    public List<RuleFinding> evaluate(GuardEvaluationContext context) {
        List<RuleFinding> findings = new ArrayList<>();
        Mandate mandate = context.mandate();
        GuardCart cart = context.proposedCart();
        if (mandate == null) {
            findings.add(finding("MANDATE_EXISTS", "MANDATE", FAIL, BLOCK, "MANDATE_NOT_FOUND",
                    "No active mandate is available for this journey.", values("mandateId", context.mandateId())));
            return findings;
        }

        findings.add(finding("JOURNEY_MANDATE_LINK", "MANDATE", PASS, NONE, "JOURNEY_MANDATE_MATCH",
                "The mandate is linked to this journey.", values("journeyId", context.journeyId())));
        if (mandate.source() == Mandate.Source.SIMULATED_AP2 && allowSimulatedMandates) {
            findings.add(finding("MANDATE_VERIFICATION", "MANDATE", PASS, NONE,
                    "SIMULATED_MANDATE_ACCEPTED", "Simulated mandate accepted by local development policy.",
                    values("source", mandate.source().name(), "simulated", true)));
        } else {
            findings.add(finding("MANDATE_VERIFICATION", "MANDATE", RuleFinding.Result.REVIEW, RuleFinding.Effect.REVIEW,
                    "MANDATE_NOT_VERIFIED", "Authorization evidence is not verified by this engine.",
                    values("source", mandate.source() == null ? null : mandate.source().name())));
        }

        if (mandate.expiresAt() == null || !context.evaluatedAt().isBefore(mandate.expiresAt())) {
            findings.add(finding("MANDATE_EXPIRY", "MANDATE", FAIL, BLOCK, "MANDATE_EXPIRED",
                    "The mandate is expired or has no expiry time.",
                    values("expiresAt", mandate.expiresAt(), "evaluatedAt", context.evaluatedAt())));
        } else {
            findings.add(finding("MANDATE_EXPIRY", "MANDATE", PASS, NONE, "MANDATE_ACTIVE",
                    "The mandate has not expired.", Map.of()));
        }

        if (cart == null) {
            findings.add(finding("CART_CONTEXT", "REQUEST_INTEGRITY", RuleFinding.Result.REVIEW,
                    RuleFinding.Effect.REVIEW,
                    "INSUFFICIENT_INFORMATION", "The proposed cart is missing.", Map.of()));
            return findings;
        }
        if (!same(mandate.currency(), cart.currency())) {
            findings.add(finding("CART_CURRENCY_MATCH", "CART", FAIL, BLOCK, "CURRENCY_MISMATCH",
                    "The cart currency does not match the mandate.",
                    values("authorizedCurrency", mandate.currency(), "cartCurrency", cart.currency())));
        } else {
            findings.add(finding("CART_CURRENCY_MATCH", "CART", PASS, NONE, "CURRENCY_MATCH",
                    "The cart currency matches the mandate.", values("currency", cart.currency())));
        }

        if (mandate.maxAmount() == null || cart.total() == null) {
            findings.add(finding("CART_AMOUNT_WITHIN_LIMIT", "CART", RuleFinding.Result.REVIEW,
                    RuleFinding.Effect.REVIEW,
                    "INSUFFICIENT_INFORMATION", "The mandate limit or proposed total is missing.",
                    values("authorizedMaximum", mandate.maxAmount(), "proposedTotal", cart.total())));
        } else if (cart.total().compareTo(mandate.maxAmount()) > 0) {
            findings.add(finding("CART_AMOUNT_WITHIN_LIMIT", "CART", FAIL, BLOCK,
                    "AMOUNT_EXCEEDS_MANDATE", "The resulting cart total exceeds the mandate's maximum amount.",
                    values("authorizedMaximum", mandate.maxAmount(), "proposedTotal", cart.total(),
                            "currency", cart.currency())));
        } else {
            findings.add(finding("CART_AMOUNT_WITHIN_LIMIT", "CART", PASS, NONE,
                    "AMOUNT_WITHIN_MANDATE", "The resulting cart total is within the mandate limit.",
                    values("authorizedMaximum", mandate.maxAmount(), "proposedTotal", cart.total())));
        }

        if (cart.items().isEmpty() && "CHECKOUT".equals(context.operation())) {
            findings.add(finding("CHECKOUT_CART_PURCHASABLE", "CHECKOUT", FAIL, BLOCK,
                    "CART_NOT_PURCHASABLE", "An empty cart cannot be checked out.", Map.of()));
        }
        for (GuardCart.Item item : cart.items()) {
            if (item.quantity() <= 0 || item.productId() == null || item.productId().isBlank()
                    || item.category() == null || item.category().isBlank() || item.unitPrice() == null) {
                findings.add(finding("PRODUCT_CONTEXT_VALID", "CART", FAIL, BLOCK,
                        "UNTRUSTED_PRODUCT_CONTEXT", "A cart item is malformed.", Map.of()));
                continue;
            }
            if (!mandate.allowedCategories().isEmpty() && mandate.allowedCategories().stream()
                    .noneMatch(category -> category.equalsIgnoreCase(item.category().trim()))) {
                findings.add(finding("CART_CATEGORY_AUTHORIZED", "CART", FAIL, BLOCK,
                        "CATEGORY_NOT_AUTHORIZED", "A cart item category is outside the mandate.",
                        values("productId", item.productId(), "category", item.category(),
                                "allowedCategories", mandate.allowedCategories())));
            }
        }

        if (mandate.shippingCountry() == null || mandate.shippingCountry().isBlank()) {
            findings.add(finding("SHIPPING_DESTINATION_AUTHORIZED", "CART", NOT_APPLICABLE, NONE,
                    "SHIPPING_UNRESTRICTED", "The mandate does not restrict the shipping country.", Map.of()));
        } else if (cart.shippingCountry() == null || cart.shippingCountry().isBlank()) {
            RuleFinding.Result result = "CHECKOUT".equals(context.operation())
                    ? RuleFinding.Result.REVIEW : NOT_APPLICABLE;
            RuleFinding.Effect effect = result == RuleFinding.Result.REVIEW
                    ? RuleFinding.Effect.REVIEW : NONE;
            findings.add(finding("SHIPPING_DESTINATION_AUTHORIZED", "CART", result, effect,
                    result == RuleFinding.Result.REVIEW ? "CHECKOUT_INFORMATION_MISSING" : "SHIPPING_NOT_SELECTED",
                    result == RuleFinding.Result.REVIEW ? "The authorized shipping country is required at checkout."
                            : "Shipping has not been selected yet.",
                    values("authorizedCountry", mandate.shippingCountry())));
        } else if (!same(mandate.shippingCountry(), cart.shippingCountry())) {
            findings.add(finding("SHIPPING_DESTINATION_AUTHORIZED", "CART", FAIL, BLOCK,
                    "SHIPPING_DESTINATION_NOT_AUTHORIZED", "The shipping country does not match the mandate.",
                    values("authorizedCountry", mandate.shippingCountry(), "observedCountry", cart.shippingCountry())));
        } else {
            findings.add(finding("SHIPPING_DESTINATION_AUTHORIZED", "CART", PASS, NONE,
                    "SHIPPING_DESTINATION_MATCH", "The shipping country matches the mandate.",
                    values("country", cart.shippingCountry())));
        }

        if (context.currentCart() != null && !"CHECKOUT".equals(context.operation())) {
            findings.add(finding("CART_MUTATION_OBSERVED", "CART", NOT_APPLICABLE, NONE,
                    "CART_MUTATION_OBSERVED", "The proposed cart was compared with the current cart.",
                    values("currentTotal", context.currentCart().total(), "proposedTotal", cart.total(),
                            "currentItemCount", context.currentCart().items().size(),
                            "proposedItemCount", cart.items().size())));
        }
        return findings;
    }

    private RuleFinding finding(String id, String group, RuleFinding.Result result,
                                RuleFinding.Effect effect, String code, String message,
                                Map<String, Object> evidence) {
        return new RuleFinding(id, group, evaluatorType(), result, effect, code, message, evidence, null, Map.of());
    }
    private Map<String, Object> values(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) if (values[i + 1] != null) {
            map.put(values[i].toString(), values[i + 1]);
        }
        return map;
    }
    private boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
