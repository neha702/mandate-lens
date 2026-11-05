package com.emeralds.mandatelens.merchant.integration.storesync.service;

import com.emeralds.mandatelens.merchant.cart.model.Cart;
import com.emeralds.mandatelens.merchant.cart.model.CartItemRequest;
import com.emeralds.mandatelens.merchant.cart.model.CartLine;
import com.emeralds.mandatelens.merchant.cart.service.CartService;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCartResponse;
import com.emeralds.mandatelens.merchant.integration.storesync.model.StoreSyncCheckoutRequest;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository;
import com.emeralds.mandatelens.merchant.integration.storesync.repository.StoreSyncOrderRepository.GuardContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Maps Store Sync wire requests to the merchant cart and PayPal Orders v2. */
@Service
public class StoreSyncCartService {
    private static final Logger logger = LoggerFactory.getLogger(StoreSyncCartService.class);

    private final CartService cartService;
    private final PayPalOrdersClient ordersClient;
    private final StoreSyncOrderRepository orderRepository;
    private final StoreSyncGuardService agentGuard;
    private final String demoJourneyId;
    private final String demoMandateId;
    private final boolean paypalSandboxEnabled;
    private final String paypalReturnUrl;
    private final String paypalCancelUrl;

    public StoreSyncCartService(CartService cartService, PayPalOrdersClient ordersClient,
                                StoreSyncOrderRepository orderRepository,
                                StoreSyncGuardService agentGuard,
                                @Value("${merchant.demo.journey-id:demo-shoes-001}") String demoJourneyId,
                                @Value("${merchant.demo.mandate-id:mandate-demo-shoes-001}") String demoMandateId,
                                @Value("${paypal.sandbox.enabled:false}") boolean paypalSandboxEnabled,
                                @Value("${paypal.orders.return-url:http://localhost:5173/checkout/return}") String paypalReturnUrl,
                                @Value("${paypal.orders.cancel-url:http://localhost:5173/checkout/cancel}") String paypalCancelUrl) {
        this.cartService = cartService;
        this.ordersClient = ordersClient;
        this.orderRepository = orderRepository;
        this.agentGuard = agentGuard;
        this.demoJourneyId = demoJourneyId;
        this.demoMandateId = demoMandateId;
        this.paypalSandboxEnabled = paypalSandboxEnabled;
        this.paypalReturnUrl = paypalReturnUrl;
        this.paypalCancelUrl = paypalCancelUrl;
    }

    public StoreSyncCartResponse create(StoreSyncCartRequest request) throws IOException {
        GuardContext guardContext = resolveGuardContext(request);
        Cart cart = cartService.previewCreate(guardContext.journeyId(), toCartItems(request.items()));
        StoreSyncOrderRepository.CartDetails details = cartDetails(request);
        logger.info("Store Sync cart create: cartId={} journeyId={} itemCount={} total={} currency={}",
                cart.id(), guardContext.journeyId(), cart.items().size(), cart.subtotal(), cart.currency());
        String operationId = agentGuard.checkCartAdd(cart.id(), guardContext, cart, country(details));
        StoreSyncOrderRepository.OrderState order = null;
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        try {
            cartService.commitCreate(cart);
            orderRepository.saveCartDetails(cart.id(), details);
            orderRepository.saveGuardContext(cart.id(), guardContext);
            if (issues.isEmpty() && !guardContext.deferPaymentUntilApproval()) {
                order = createPaymentOrder(cart, details);
                orderRepository.saveOrder(cart.id(), order);
            }
        } catch (RuntimeException exception) {
            logger.error("Store Sync cart create failed: cartId={} journeyId={} total={} currency={} errorType={}",
                    cart.id(), guardContext.journeyId(), cart.subtotal(), cart.currency(),
                    exception.getClass().getSimpleName(), exception);
            reportFailure(operationId, guardContext, cart.id(), "CART_ADD_FAILED", cart,
                    null, "CART_ADD_FAILED", exception.getMessage(), List.of(), exception);
            throw exception;
        }
        agentGuard.reportResult(operationId, guardContext, cart.id(), "CART_ADD_SUCCEEDED", "SUCCESS",
                cart.subtotal(), cart.currency(), null, null, null, null, null, List.of());
        logger.info("Store Sync cart created: cartId={} status={} validationIssueCount={} paypalOrderCreated={}",
                cart.id(), issues.isEmpty() ? "CREATED" : "INCOMPLETE", issues.size(), order != null);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE", order, issues);
    }

    public StoreSyncCartResponse replace(String cartId, StoreSyncCartRequest request) throws IOException {
        if (orderRepository.findCompletedCart(cartId).isPresent()) {
            throw new IllegalStateException("A completed cart cannot be changed.");
        }
        StoreSyncOrderRepository.CartDetails previousDetails = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext guardContext = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        if (guardContext.checkoutOperationId() != null) {
            throw new IllegalStateException("Checkout is awaiting the shopper's decision. Approve or decline it before editing the cart.");
        }
        Cart currentCart = cartService.get(cartId);
        Cart cart = cartService.previewReplace(cartId, toCartItems(request.items()));
        StoreSyncOrderRepository.CartDetails details = cartDetails(request);
        boolean removalOnly = isRemovalOnly(currentCart, cart);
        StoreSyncOrderRepository.OrderState existingOrder = orderRepository.findOrder(cartId).orElse(null);
        if (!removalOnly && !cart.items().isEmpty() && existingOrder != null) {
            ensurePaymentModeMatches(existingOrder);
        }
        logger.info("Store Sync cart replace: cartId={} currentTotal={} proposedTotal={} currency={} itemCount={} removalOnly={}",
                cartId, currentCart.subtotal(), cart.subtotal(), cart.currency(), cart.items().size(), removalOnly);
        String operationId;
        if (removalOnly) {
            operationId = agentGuard.newResultOnlyOperationId();
        } else if (currentCart.items().isEmpty()) {
            operationId = agentGuard.checkCartAdd(cartId, guardContext, cart, country(details));
        } else {
            operationId = agentGuard.checkCartUpdate(cartId, guardContext, currentCart, cart, country(details));
        }
        String resultEventType = removalOnly ? "ITEM_REMOVAL_SUCCEEDED" : "CART_UPDATE_SUCCEEDED";
        String failureEventType = removalOnly ? "ITEM_REMOVAL_FAILED" : "CART_UPDATE_FAILED";
        List<com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests.ChangedItem> removedItems =
                removalOnly ? removedItems(currentCart, cart) : List.of();
        StoreSyncOrderRepository.OrderState order = existingOrder;
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        try {
            cartService.commitReplace(cart);
            orderRepository.saveCartDetails(cart.id(), details);
            if (removalOnly && cart.items().isEmpty()) {
                if (order != null) orderRepository.cancelPendingOrder(cartId, order.orderId());
                order = null;
            } else if (issues.isEmpty() && !guardContext.deferPaymentUntilApproval()) {
                if (order == null) {
                    order = createPaymentOrder(cart, details);
                } else {
                    int revision = order.revision() + 1;
                    if (!isDemoOrder(order)) {
                        ordersClient.updateOrder(cartId, revision, order.orderId(), amount(cart), paypalItems(cart),
                                shippingDetails(details), previousDetails != null && previousDetails.shippingAddress() != null);
                    }
                    order = new StoreSyncOrderRepository.OrderState(order.orderId(), revision,
                            order.approvalUrl(), order.provider());
                }
                orderRepository.saveOrder(cartId, order);
            }
        } catch (RuntimeException exception) {
            logger.error("Store Sync cart replace failed: cartId={} proposedTotal={} currency={} errorType={}",
                    cartId, cart.subtotal(), cart.currency(), exception.getClass().getSimpleName(), exception);
            reportFailure(operationId, guardContext, cartId, failureEventType, cart,
                    null, failureEventType, exception.getMessage(), removedItems, exception);
            throw exception;
        }
        agentGuard.reportResult(operationId, guardContext, cartId, resultEventType, "SUCCESS",
                cart.subtotal(), cart.currency(), order == null ? null : order.orderId(), null, null,
                null, null, removedItems);
        orderRepository.saveGuardContext(cartId, new GuardContext(guardContext.journeyId(), guardContext.mandateId(),
                guardContext.deferPaymentUntilApproval()));
        logger.info("Store Sync cart replaced: cartId={} status={} validationIssueCount={} paypalOrderPresent={}",
                cartId, issues.isEmpty() ? "CREATED" : "INCOMPLETE", issues.size(), order != null);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE", order, issues);
    }

    /** Keeps an open Merchant cart while linking future checks to a freshly approved journey. */
    public StoreSyncCartResponse attachToNewJourney(String cartId, String journeyId, String mandateId) {
        if (orderRepository.findCompletedCart(cartId).isPresent()) {
            throw new IllegalStateException("A completed cart cannot be attached to a new journey.");
        }
        GuardContext previous = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        if (previous.checkoutOperationId() != null) {
            throw new IllegalStateException("Checkout is awaiting the shopper's decision. Approve or decline it before starting a new journey.");
        }
        if (journeyId == null || journeyId.isBlank() || mandateId == null || mandateId.isBlank()) {
            throw new IllegalArgumentException("journeyId and mandateId are required.");
        }
        cartService.rebindJourney(cartId, journeyId);
        orderRepository.saveGuardContext(cartId, new GuardContext(journeyId, mandateId, true));
        Cart cart = cartService.get(cartId);
        StoreSyncOrderRepository.OrderState order = orderRepository.findOrder(cartId).orElse(null);
        return response(cart, cart.availabilityIssues().isEmpty() ? "CREATED" : "INCOMPLETE", order,
                inventoryIssues(cart));
    }

    public StoreSyncCartResponse get(String cartId) {
        StoreSyncCartResponse completed = orderRepository.findCompletedCart(cartId).orElse(null);
        if (completed != null) return completed;
        Cart cart = cartService.get(cartId);
        List<StoreSyncCartResponse.ValidationIssue> issues = inventoryIssues(cart);
        return response(cart, issues.isEmpty() ? "CREATED" : "INCOMPLETE",
                orderRepository.findOrder(cartId).orElse(null), issues);
    }

    /** Guard-only checkout review. No order or payment is created here. */
    public CheckoutPreviewResponse previewCheckout(String cartId) {
        if (orderRepository.findCompletedCart(cartId).isPresent()) {
            throw new IllegalStateException("This cart has already completed checkout.");
        }
        Cart cart = cartService.get(cartId);
        if (cart.items().isEmpty()) throw new IllegalArgumentException("Cannot check out an empty cart.");
        if (!cart.availabilityIssues().isEmpty()) throw new IllegalArgumentException("Cart has unavailable items.");
        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext context = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        logger.info("Store Sync checkout preview started: cartId={} journeyId={} total={} currency={} itemCount={}",
                cartId, context.journeyId(), cart.subtotal(), cart.currency(), cart.items().size());
        if (context.checkoutOperationId() != null) {
            logger.info("Store Sync checkout preview reused pending Guard approval: cartId={} journeyId={} operationId={}",
                    cartId, context.journeyId(), context.checkoutOperationId());
            return new CheckoutPreviewResponse(context.checkoutOperationId(), cartId, context.journeyId(),
                    cart.subtotal(), cart.currency(), "ALLOW",
                    "This cart already has a Guard review awaiting the shopper's decision.",
                    context.checkoutGuardResult());
        }
        if (context.checkoutGuardResult() != null) {
            String decision = context.checkoutGuardResult().path("decision").asText("BLOCK");
            logger.info("Store Sync checkout preview reused saved Guard decision: cartId={} journeyId={} decision={}",
                    cartId, context.journeyId(), decision);
            return new CheckoutPreviewResponse(null, cartId, context.journeyId(), cart.subtotal(),
                    cart.currency(), decision, "Showing the latest Guard result for this unchanged cart.",
                    context.checkoutGuardResult());
        }
        StoreSyncGuardService.CheckoutGuardResult guardResult;
        try {
            guardResult = agentGuard.checkCheckoutDetailed(cartId, context, cart, country(details));
        } catch (RuntimeException exception) {
            logger.error("Store Sync checkout preview failed: cartId={} journeyId={} total={} currency={} errorType={}",
                    cartId, context.journeyId(), cart.subtotal(), cart.currency(),
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        }
        String operationId = "ALLOW".equals(guardResult.decision()) ? guardResult.operationId() : null;
        logger.info("Store Sync checkout preview completed: cartId={} journeyId={} decision={} operationId={}",
                cartId, context.journeyId(), guardResult.decision(), operationId);
        orderRepository.saveGuardContext(cartId, new GuardContext(context.journeyId(), context.mandateId(),
                context.deferPaymentUntilApproval(), operationId, guardResult.details()));
        return new CheckoutPreviewResponse(operationId, cartId, context.journeyId(), cart.subtotal(),
                cart.currency(), guardResult.decision(),
                "ALLOW".equals(guardResult.decision())
                        ? "Guard allowed this cart. Waiting for shopper approval; no payment has happened."
                        : "Guard blocked this cart. Review the reasons and update the cart before trying again.",
                guardResult.details());
    }

    /** Simulated MCP checkout settlement after the shopper explicitly approves the reviewed cart. */
    public CheckoutApprovalResponse approveCheckout(String cartId, CheckoutApprovalRequest request) {
        if (request == null || request.operationId() == null || request.operationId().isBlank()) {
            throw new IllegalArgumentException("operationId is required from the checkout review.");
        }
        if (paypalSandboxEnabled) {
            throw new IllegalStateException("MCP checkout is currently simulated. Turn PayPal Sandbox off or use the merchant storefront checkout.");
        }
        StoreSyncCartResponse completed = orderRepository.findCompletedCart(cartId).orElse(null);
        if (completed != null) return new CheckoutApprovalResponse("COMPLETED", cartId,
                completed.paymentConfirmation() == null ? null : completed.paymentConfirmation().merchantOrderNumber(),
                "This cart was already paid in the demo.");
        Cart cart = cartService.get(cartId);
        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext context = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        requirePendingCheckout(context, request.operationId());
        StoreSyncOrderRepository.OrderState order = createPaymentOrder(cart, details);
        String captureId = "DEMO-CAPTURE-" + UUID.randomUUID();
        agentGuard.reportResult(request.operationId(), context, cartId, "PAYMENT_SUCCEEDED", "SUCCESS",
                cart.subtotal(), cart.currency(), order.orderId(), null,
                "Shopper approved the reviewed cart; demo payment completed without moving money.",
                order.orderId(), captureId, List.of());
        orderRepository.saveOrder(cartId, order);
        StoreSyncCartResponse completedResponse = response(cart, "COMPLETED", order, List.of(),
                new StoreSyncCartResponse.PaymentConfirmation(order.orderId(), null));
        orderRepository.saveCompletedCart(cartId, completedResponse, captureId);
        orderRepository.saveGuardContext(cartId, new GuardContext(context.journeyId(), context.mandateId(),
                context.deferPaymentUntilApproval()));
        return new CheckoutApprovalResponse("COMPLETED", cartId, order.orderId(),
                "Approved. Simulated payment completed; no PayPal Sandbox or real money was used.");
    }

    /** Close a reviewed checkout as declined without creating an order or taking payment. */
    public CheckoutApprovalResponse declineCheckout(String cartId, CheckoutApprovalRequest request) {
        if (request == null || request.operationId() == null || request.operationId().isBlank()) {
            throw new IllegalArgumentException("operationId is required from the checkout review.");
        }
        Cart cart = cartService.get(cartId);
        GuardContext context = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        requirePendingCheckout(context, request.operationId());
        agentGuard.reportResult(request.operationId(), context, cartId, "PAYMENT_FAILED", "FAILED",
                cart.subtotal(), cart.currency(), null, "SHOPPER_DECLINED",
                "Shopper declined checkout; no payment was attempted.", null, null, List.of());
        orderRepository.saveGuardContext(cartId, new GuardContext(context.journeyId(), context.mandateId(),
                context.deferPaymentUntilApproval()));
        return new CheckoutApprovalResponse("DECLINED", cartId, null,
                "Checkout declined. The cart remains available and no payment was made.");
    }

    public StoreSyncCartResponse checkout(String cartId, StoreSyncCheckoutRequest request) throws IOException {
        StoreSyncCartResponse completed = orderRepository.findCompletedCart(cartId).orElse(null);
        if (completed != null) return completed;
        Cart cart = cartService.get(cartId);
        if (!cart.availabilityIssues().isEmpty()) {
            return response(cart, "INCOMPLETE", orderRepository.findOrder(cartId).orElse(null), inventoryIssues(cart));
        }
        if (request.paymentMethod() == null || request.paymentMethod().type() == null) {
            throw new IllegalArgumentException("payment_method is required.");
        }
        StoreSyncOrderRepository.OrderState order = orderRepository.findOrder(cartId).orElse(null);
        String requestedOrderId = request.paymentMethod().token();
        if (order == null || requestedOrderId == null || !order.orderId().equals(requestedOrderId)) {
            throw new IllegalArgumentException("payment_method.token must match this cart's payment order ID.");
        }
        ensurePaymentModeMatches(order);
        String expectedPaymentType = isDemoOrder(order) ? "demo" : "paypal";
        if (!expectedPaymentType.equalsIgnoreCase(request.paymentMethod().type())) {
            throw new IllegalArgumentException("payment_method.type must be " + expectedPaymentType + " for this cart.");
        }

        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cartId).orElse(null);
        GuardContext guardContext = orderRepository.findGuardContext(cartId)
                .orElseThrow(() -> new IllegalStateException("Cart has no journey-to-mandate mapping."));
        logger.info("Store Sync checkout started: cartId={} total={} currency={} itemCount={}",
                cartId, cart.subtotal(), cart.currency(), cart.items().size());
        String operationId = agentGuard.checkCheckout(cartId, guardContext, cart, country(details));

        PayPalOrdersClient.CaptureResult capture;
        if (isDemoOrder(order)) {
            capture = new PayPalOrdersClient.CaptureResult("COMPLETED", "DEMO-CAPTURE-" + UUID.randomUUID());
        } else try {
            capture = ordersClient.captureOrder(cartId, order.orderId());
        } catch (RuntimeException exception) {
            logger.error("Store Sync PayPal capture failed: cartId={} total={} currency={} errorType={}",
                    cartId, cart.subtotal(), cart.currency(), exception.getClass().getSimpleName(), exception);
            reportFailure(operationId, guardContext, cartId, "PAYMENT_FAILED", cart, order.orderId(),
                    "PAYMENT_ERROR", exception.getMessage(), List.of(), exception);
            if (exception instanceof PayPalOrdersClient.PayPalGatewayException gatewayException
                    && gatewayException.httpStatus() == 422) {
                StoreSyncCartResponse.ValidationIssue paymentIssue = new StoreSyncCartResponse.ValidationIssue(
                        "PAYMENT_ERROR", "BUSINESS_RULE", "PayPal could not complete this payment.",
                        "Choose another payment method and try again.", null);
                return response(cart, "INCOMPLETE", order, List.of(paymentIssue));
            }
            throw exception;
        }
        StoreSyncCartResponse response = response(cart, "COMPLETED", order, List.of(),
                new StoreSyncCartResponse.PaymentConfirmation(order.orderId(), null));
        orderRepository.saveCompletedCart(cartId, response, capture.captureId());
        agentGuard.reportResult(operationId, guardContext, cartId, "PAYMENT_SUCCEEDED", "SUCCESS",
                cart.subtotal(), cart.currency(), order.orderId(), null, null,
                isDemoOrder(order) ? null : order.orderId(),
                isDemoOrder(order) ? null : capture.captureId(), List.of());
        logger.info("Store Sync checkout completed: cartId={} total={} currency={}",
                cartId, cart.subtotal(), cart.currency());
        return response;
    }

    private void reportFailure(String operationId, GuardContext context, String cartId, String eventType,
                               Cart cart, String reference, String failureCode, String message,
                               List<com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests.ChangedItem> changedItems,
                               Exception originalFailure) {
        try {
            agentGuard.reportResult(operationId, context, cartId, eventType, "FAILED", cart.subtotal(),
                    cart.currency(), reference, failureCode, message, null, null, changedItems);
        } catch (RuntimeException callbackException) {
            originalFailure.addSuppressed(callbackException);
        }
    }

    private List<CartItemRequest> toCartItems(List<StoreSyncCartRequest.Item> items) {
        if (items == null) throw new IllegalArgumentException("items is required.");
        return items.stream().map(item -> new CartItemRequest(item.catalogVariantId(), item.quantity())).toList();
    }

    private boolean isRemovalOnly(Cart current, Cart proposed) {
        Map<String, CartLine> currentById = new HashMap<>();
        current.items().forEach(item -> currentById.put(item.variantId(), item));
        Map<String, CartLine> proposedById = new HashMap<>();
        proposed.items().forEach(item -> proposedById.put(item.variantId(), item));

        boolean quantityRemoved = false;
        for (CartLine next : proposed.items()) {
            CartLine previous = currentById.get(next.variantId());
            if (previous == null || next.quantity() > previous.quantity()
                    || next.unitPrice().compareTo(previous.unitPrice()) != 0
                    || !Objects.equals(next.category(), previous.category())) return false;
            if (next.quantity() < previous.quantity()) quantityRemoved = true;
        }
        for (CartLine previous : current.items()) {
            if (!proposedById.containsKey(previous.variantId())) quantityRemoved = true;
        }
        return quantityRemoved;
    }

    private List<com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests.ChangedItem> removedItems(
            Cart current, Cart proposed) {
        Map<String, Integer> remaining = new HashMap<>();
        proposed.items().forEach(item -> remaining.put(item.variantId(), item.quantity()));
        return current.items().stream().map(previous -> {
            int quantityLeft = remaining.getOrDefault(previous.variantId(), 0);
            int removedQuantity = previous.quantity() - quantityLeft;
            return removedQuantity > 0
                    ? new com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests.ChangedItem(
                    previous.variantId(), -removedQuantity)
                    : null;
        }).filter(java.util.Objects::nonNull).toList();
    }

    private Map<String, Object> orderBody(Cart cart, StoreSyncOrderRepository.CartDetails details) {
        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("reference_id", "default");
        purchaseUnit.put("amount", amount(cart));
        purchaseUnit.put("items", paypalItems(cart));
        Map<String, Object> shipping = shippingDetails(details);
        if (shipping != null) purchaseUnit.put("shipping", shipping);
        Map<String, Object> experienceContext = Map.of(
                "return_url", appendCartId(paypalReturnUrl, cart.id()),
                "cancel_url", appendCartId(paypalCancelUrl, cart.id()),
                "shipping_preference", "SET_PROVIDED_ADDRESS",
                "user_action", "PAY_NOW");
        Map<String, Object> paymentSource = Map.of("paypal", Map.of("experience_context", experienceContext));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("payment_source", paymentSource);
        body.put("purchase_units", List.of(purchaseUnit));
        return body;
    }

    private StoreSyncOrderRepository.OrderState createPaymentOrder(
            Cart cart, StoreSyncOrderRepository.CartDetails details) {
        if (!paypalSandboxEnabled) {
            String demoOrderId = "DEMO-ORDER-" + UUID.randomUUID();
            logger.info("Simulated payment order created: cartId={}", cart.id());
            return new StoreSyncOrderRepository.OrderState(demoOrderId, 0, null, "DEMO");
        }
        PayPalOrdersClient.OrderResult result = ordersClient.createOrder(cart.id(), orderBody(cart, details));
        return new StoreSyncOrderRepository.OrderState(result.orderId(), 0, result.approvalUrl(), "PAYPAL");
    }

    private boolean isDemoOrder(StoreSyncOrderRepository.OrderState order) {
        return "DEMO".equalsIgnoreCase(order.provider())
                || (order.provider() == null && order.orderId().startsWith("DEMO-"));
    }

    private void ensurePaymentModeMatches(StoreSyncOrderRepository.OrderState order) {
        boolean orderUsesSandbox = !isDemoOrder(order);
        if (orderUsesSandbox != paypalSandboxEnabled) {
            throw new IllegalStateException("This cart was created in a different payment mode. Clear the cart and start a new one after changing PAYPAL_SANDBOX_ENABLED.");
        }
    }

    private String appendCartId(String baseUrl, String cartId) {
        return baseUrl + (baseUrl.contains("?") ? "&" : "?")
                + "cartId=" + URLEncoder.encode(cartId, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> paypalItems(Cart cart) {
        return cart.items().stream().map(item -> Map.<String, Object>of(
                "name", item.name(),
                "quantity", Integer.toString(item.quantity()),
                "unit_amount", money(item.unitPrice(), item.currency()))).toList();
    }

    private Map<String, Object> shippingDetails(StoreSyncOrderRepository.CartDetails details) {
        if (details == null || details.shippingAddress() == null) return null;
        StoreSyncCartRequest.Address address = details.shippingAddress();
        Map<String, Object> paypalAddress = new LinkedHashMap<>();
        putIfPresent(paypalAddress, "address_line_1", address.addressLine1());
        putIfPresent(paypalAddress, "address_line_2", address.addressLine2());
        putIfPresent(paypalAddress, "admin_area_2", address.adminArea2());
        putIfPresent(paypalAddress, "admin_area_1", address.adminArea1());
        putIfPresent(paypalAddress, "postal_code", address.postalCode());
        putIfPresent(paypalAddress, "country_code", address.countryCode());

        Map<String, Object> shipping = new LinkedHashMap<>();
        StoreSyncCartRequest.Customer customer = details.customer();
        if (customer != null && customer.name() != null) {
            String fullName = String.join(" ", java.util.stream.Stream.of(
                    customer.name().givenName(), customer.name().surname())
                    .filter(value -> value != null && !value.isBlank()).toList());
            putIfPresent(shipping, "name", fullName.isBlank() ? null : Map.of("full_name", fullName));
        }
        if (customer != null) putIfPresent(shipping, "email_address", customer.emailAddress());
        shipping.put("address", paypalAddress);
        return shipping;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private Map<String, Object> amount(Cart cart) {
        Map<String, Object> subtotal = money(cart.subtotal(), cart.currency());
        return Map.of("currency_code", cart.currency(), "value", decimal(cart.subtotal()),
                "breakdown", Map.of("item_total", subtotal));
    }

    private Map<String, Object> money(BigDecimal amount, String currency) {
        return Map.of("currency_code", currency, "value", decimal(amount));
    }

    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private StoreSyncCartResponse response(Cart cart, String status, StoreSyncOrderRepository.OrderState order,
                                           List<StoreSyncCartResponse.ValidationIssue> issues) {
        return response(cart, status, order, issues, null);
    }

    private StoreSyncCartResponse response(Cart cart, String status, StoreSyncOrderRepository.OrderState order,
                                          List<StoreSyncCartResponse.ValidationIssue> issues,
                                          StoreSyncCartResponse.PaymentConfirmation confirmation) {
        List<StoreSyncCartResponse.Item> items = cart.items().stream().map(item ->
                new StoreSyncCartResponse.Item(item.variantId(), item.quantity(), item.name(),
                        new StoreSyncCartResponse.Money(item.currency(), decimal(item.unitPrice())),
                        new StoreSyncCartResponse.Money(item.currency(),
                                decimal(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))))).toList();
        StoreSyncCartResponse.Money total = new StoreSyncCartResponse.Money(cart.currency(), decimal(cart.subtotal()));
        StoreSyncCartResponse.Totals totals = new StoreSyncCartResponse.Totals(total, total, zero(cart.currency()), zero(cart.currency()));
        StoreSyncCartResponse.PaymentMethod paymentMethod = order == null ? null
                : new StoreSyncCartResponse.PaymentMethod(isDemoOrder(order) ? "demo" : "paypal",
                        order.orderId(), null, order.approvalUrl());
        String validationStatus = issues.isEmpty() ? "VALID" : "INVALID";
        StoreSyncOrderRepository.CartDetails details = orderRepository.findCartDetails(cart.id()).orElse(null);
        return new StoreSyncCartResponse(cart.id(), status, validationStatus, issues, items, totals,
                details == null ? null : details.customer(),
                details == null ? null : details.shippingAddress(),
                details == null ? null : details.billingAddress(),
                paymentMethod, confirmation);
    }

    private StoreSyncOrderRepository.CartDetails cartDetails(StoreSyncCartRequest request) {
        return new StoreSyncOrderRepository.CartDetails(
                request.customer(), request.shippingAddress(), request.billingAddress());
    }

    private String country(StoreSyncOrderRepository.CartDetails details) {
        return details == null || details.shippingAddress() == null ? null
                : details.shippingAddress().countryCode();
    }

    private void requirePendingCheckout(GuardContext context, String operationId) {
        if (context.checkoutOperationId() == null || !context.checkoutOperationId().equals(operationId)) {
            throw new IllegalArgumentException("operationId does not match the active Guard checkout review for this cart.");
        }
    }

    private GuardContext resolveGuardContext(StoreSyncCartRequest request) {
        boolean hasJourney = request.journeyId() != null && !request.journeyId().isBlank();
        boolean hasMandate = request.mandateId() != null && !request.mandateId().isBlank();
        if (hasJourney || hasMandate) {
            if (!hasJourney || !hasMandate) {
                throw new IllegalArgumentException("journey_id and mandate_id must be provided together.");
            }
            return new GuardContext(request.journeyId(), request.mandateId(), true);
        }
        if (demoJourneyId == null || demoJourneyId.isBlank() || demoMandateId == null || demoMandateId.isBlank()) {
            throw new IllegalStateException("Merchant demo journey and mandate IDs must be configured.");
        }
        return new GuardContext(demoJourneyId, demoMandateId);
    }

    private StoreSyncCartResponse.Money zero(String currency) { return new StoreSyncCartResponse.Money(currency, "0.00"); }

    private List<StoreSyncCartResponse.ValidationIssue> inventoryIssues(Cart cart) {
        List<StoreSyncCartResponse.ValidationIssue> issues = new ArrayList<>();
        for (String variantId : cart.availabilityIssues()) {
            issues.add(new StoreSyncCartResponse.ValidationIssue("INVENTORY_ISSUE", "BUSINESS_RULE",
                    "Item is unavailable in the requested quantity.", "Please adjust the item quantity or choose another item.", variantId));
        }
        return issues;
    }

    public record CheckoutPreviewResponse(String operationId, String cartId, String journeyId,
                                          BigDecimal total, String currency, String decision,
                                          String message, JsonNode guardResult) { }
    public record CheckoutApprovalRequest(String operationId, boolean approved) { }
    public record CheckoutApprovalResponse(String status, String cartId, String orderId, String message) { }
}
