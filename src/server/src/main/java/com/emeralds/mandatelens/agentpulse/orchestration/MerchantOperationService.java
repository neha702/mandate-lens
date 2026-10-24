package com.emeralds.mandatelens.agentpulse.orchestration;

import com.emeralds.mandatelens.agentguard.client.AgentGuardClient;
import com.emeralds.mandatelens.agentguard.model.EvaluateCartRequest;
import com.emeralds.mandatelens.agentguard.model.EvaluateCheckoutRequest;
import com.emeralds.mandatelens.agentguard.model.GuardCart;
import com.emeralds.mandatelens.agentguard.model.GuardDecision;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.repository.GuardDecisionRepository;
import com.emeralds.mandatelens.agentpulse.api.MerchantOperationRequests;
import com.emeralds.mandatelens.agentpulse.event.EventStore;
import com.emeralds.mandatelens.agentpulse.operation.PendingOperationRepository;
import com.emeralds.mandatelens.mandate.model.Mandate;
import com.emeralds.mandatelens.mandate.service.MandateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantOperationService {
    private final AgentGuardClient guard;
    private final MandateService mandates;
    private final PendingOperationRepository operations;
    private final GuardDecisionRepository guardDecisions;
    private final GuardDecisionRecorder decisionRecorder;
    private final EventStore eventStore;
    private final long callbackTimeoutSeconds;

    public MerchantOperationService(AgentGuardClient guard, MandateService mandates,
                                    PendingOperationRepository operations, GuardDecisionRepository guardDecisions,
                                    GuardDecisionRecorder decisionRecorder,
                                    EventStore eventStore,
                                    @Value("${mandatelens.operation.callback-timeout-seconds:300}") long callbackTimeoutSeconds) {
        this.guard = guard;
        this.mandates = mandates;
        this.operations = operations;
        this.guardDecisions = guardDecisions;
        this.decisionRecorder = decisionRecorder;
        this.eventStore = eventStore;
        this.callbackTimeoutSeconds = callbackTimeoutSeconds;
    }

    public CheckResponse check(MerchantOperationRequests.Check request) {
        validateAction(request.action());
        Mandate mandate = mandates.findByJourneyId(request.journeyId())
                .orElseThrow(() -> new OperationNotFoundException("No active mandate is linked to this journey."));
        if (!mandate.merchantId().equalsIgnoreCase(request.merchantId())) {
            throw new IllegalArgumentException("merchantId does not match the journey mandate.");
        }
        var previous = operations.findByIdempotencyKey(request.merchantId(), request.idempotencyKey());
        if (previous.isPresent()) {
            var existing = previous.get();
            if (!existing.requestHash().equals(operations.fingerprint(request))) {
                throw new IllegalArgumentException("Idempotency key was reused with different request data.");
            }
            if (existing.decisionId() == null) throw new OperationAlreadyExistsException(existing.operationId(), existing.status());
            var saved = guardDecisions.find(existing.decisionId()).orElseThrow();
            return new CheckResponse(existing.operationId(), existing.journeyId(), existing.decisionId(),
                    saved.decision(), saved.reasons(), saved.ruleResults(), saved.policyVersion(),
                    saved.evaluatedAt(), saved.contextFingerprint(), saved.findings(), existing.status());
        }

        String operationId = "op-" + UUID.randomUUID();
        Instant now = Instant.now();
        operations.create(operationId, request.journeyId(), request.merchantId(), request.cartId(),
                request.action(), request.idempotencyKey(), request, now.plusSeconds(callbackTimeoutSeconds));

        GuardDecisionResponse decision;
        try {
            GuardCart current = toGuardCart(request.cartId(), request.currentCart(), request.shippingCountry());
            GuardCart proposed = toGuardCart(request.cartId(), request.proposedCart(), request.shippingCountry());
            if ("CHECKOUT".equals(request.action())) {
                decision = guard.evaluateCheckout(new EvaluateCheckoutRequest(request.journeyId(), mandate.mandateId(), proposed));
            } else {
                decision = guard.evaluateCart(new EvaluateCartRequest(request.journeyId(), mandate.mandateId(),
                        request.action(), current, proposed));
            }
            if (decision == null || decision.decisionId() == null || decision.decision() == null) {
                throw new IllegalStateException("AgentGuard returned an incomplete decision.");
            }
        } catch (RuntimeException exception) {
            operations.markCheckFailed(operationId);
            throw exception;
        }

        GuardDecision publicDecision = decision.decision();
        decisionRecorder.record(operationId, request.journeyId(), mandate.mandateId(), request.action(),
                decision.decisionId(), publicDecision, decision.reasons(), decision.ruleResults(),
                decision.findings(), decision.policyVersion(), decision.evaluatedAt(),
                decision.contextFingerprint(), request);
        String status = publicDecision == GuardDecision.ALLOW
                ? "AWAITING_MERCHANT_RESULT" : "NOT_APPLIED";
        return new CheckResponse(operationId, request.journeyId(), decision.decisionId(),
                publicDecision, decision.reasons(), decision.ruleResults(), decision.policyVersion(),
                decision.evaluatedAt(), decision.contextFingerprint(), decision.findings(), status);
    }

    @Transactional
    public ResultResponse result(String operationId, MerchantOperationRequests.Result result) {
        var operation = operations.find(operationId);
        if (operation == null && result.eventType().startsWith("ITEM_REMOVAL_")) {
            Mandate mandate = mandates.findByJourneyId(result.journeyId())
                    .orElseThrow(() -> new OperationNotFoundException("No active mandate is linked to this journey."));
            if (!mandate.merchantId().equalsIgnoreCase(result.merchantId())) {
                throw new IllegalArgumentException("merchantId does not match the journey mandate.");
            }
            operations.createResultOnly(operationId, result.journeyId(), result.merchantId(), result.cartId(),
                    result.eventId(), result, result.occurredAt().plusSeconds(callbackTimeoutSeconds));
            operation = operations.find(operationId);
        }
        if (operation == null) throw new OperationNotFoundException("Unknown operationId.");
        if (!operation.merchantId().equalsIgnoreCase(result.merchantId())) {
            throw new IllegalArgumentException("merchantId does not match the operation.");
        }
        if (!operation.journeyId().equals(result.journeyId()) || !operation.cartId().equals(result.cartId())) {
            throw new IllegalArgumentException("Callback journeyId/cartId does not match the operation.");
        }
        if ("CHECKOUT".equals(operation.action()) != result.eventType().startsWith("PAYMENT_")) {
            throw new IllegalArgumentException("Callback event type does not match the operation action.");
        }
        if ("ITEM_REMOVAL".equals(operation.action()) != result.eventType().startsWith("ITEM_REMOVAL_")) {
            throw new IllegalArgumentException("Callback event type does not match the operation action.");
        }
        if (operation.status().equals("NOT_APPLIED")) {
            throw new IllegalStateException("A blocked operation cannot be reported as applied.");
        }
        String status = callbackStatus(result.eventType(), result.outcome());
        boolean cartEmpty = result.details().total() != null
                && result.details().total().signum() == 0;
        boolean inserted = operations.saveCallback(result.eventId(), operationId, result, status,
                result.eventType(), cartEmpty, result.occurredAt());
        if (inserted) {
            // Event is persisted with the callback in the same service transaction.
            eventStore.save(new com.emeralds.mandatelens.agentpulse.event.PurchaseEvent(result.eventId(),
                    result.journeyId(), result.occurredAt(), result.eventType(), "MERCHANT",
                    result.details().total(), result.details().currency(), "operationId=" + operationId
                    + ";outcome=" + result.outcome() + ";reference=" + result.merchantReference()
                    + ";details=" + result.details()));
        }
        return new ResultResponse(operationId, inserted, !inserted,
                inserted ? status : operation.status());
    }

    private GuardCart toGuardCart(String cartId, MerchantOperationRequests.Cart cart, String country) {
        List<GuardCart.Item> items = cart.items().stream().map(item -> {
            if (item.quantity() <= 0) throw new IllegalArgumentException("Item quantity must be positive.");
            return new GuardCart.Item(item.productId(), item.category(), item.quantity(), item.unitPrice());
        }).toList();
        return new GuardCart(cartId, items, cart.total(), cart.currency(), country);
    }

    private void validateAction(String action) {
        if (!List.of("ADD_ITEM", "UPDATE_CART", "CHECKOUT").contains(action)) {
            throw new IllegalArgumentException("action must be ADD_ITEM, UPDATE_CART, or CHECKOUT.");
        }
    }

    private String callbackStatus(String eventType, String outcome) {
        boolean success = "SUCCESS".equals(outcome);
        if (!(success || "FAILED".equals(outcome))) throw new IllegalArgumentException("outcome must be SUCCESS or FAILED.");
        if (eventType == null || !(List.of("CART_UPDATE_SUCCEEDED", "CART_UPDATE_FAILED",
                "CART_ADD_SUCCEEDED", "CART_ADD_FAILED", "ITEM_REMOVAL_SUCCEEDED",
                "ITEM_REMOVAL_FAILED", "PAYMENT_SUCCEEDED", "PAYMENT_FAILED").contains(eventType))) {
            throw new IllegalArgumentException("eventType is not a supported cart, removal, or payment result.");
        }
        boolean eventSuccess = eventType.endsWith("_SUCCEEDED");
        if (eventSuccess != success) throw new IllegalArgumentException("eventType does not match outcome.");
        return success ? "SUCCEEDED" : "FAILED";
    }

    public record CheckResponse(String operationId, String journeyId, String decisionId, GuardDecision decision,
                                List<GuardDecisionResponse.Reason> reasons,
                                List<GuardDecisionResponse.RuleResult> ruleResults, String policyVersion,
                                Instant evaluatedAt, String contextFingerprint,
                                List<com.emeralds.mandatelens.agentguard.model.RuleFinding> findings,
                                String operationStatus) { }
    public record ResultResponse(String operationId, boolean accepted, boolean alreadyReceived, String status) { }

    public static class OperationNotFoundException extends RuntimeException {
        public OperationNotFoundException(String message) { super(message); }
    }
    public static class OperationAlreadyExistsException extends RuntimeException {
        private final String operationId;
        private final String status;
        public OperationAlreadyExistsException(String operationId, String status) {
            super("This idempotency key already has an operation."); this.operationId = operationId; this.status = status;
        }
        public String operationId() { return operationId; }
        public String status() { return status; }
    }
}
