package com.emeralds.mandatelens.agentpulse.journey;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Assembles the saved journey facts without calling AgentGuard or PayPal. */
@Service
public class JourneyQueryService {
    private final JourneyReadRepository repository;

    public JourneyQueryService(JourneyReadRepository repository) { this.repository = repository; }

    public JourneyDetail getJourney(String journeyId, String merchantId) {
        var journey = repository.findJourney(journeyId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Journey not found for this merchant."));
        List<JourneyReadRepository.OperationFact> operations = repository.findOperations(journeyId);
        List<JourneyReadRepository.DecisionFact> decisions = repository.findDecisions(journeyId);
        List<JourneyReadRepository.EventFact> events = repository.findEvents(journeyId);
        var latestEvent = events.isEmpty() ? null : events.get(events.size() - 1);
        String cartId = journey.cartId();
        if (cartId == null && !operations.isEmpty()) cartId = operations.get(operations.size() - 1).cartId();
        JourneyExplanation explanation = explain(journey.status(), operations, decisions, events);
        return new JourneyDetail(journey.journeyId(), journey.merchantId(), cartId, journey.status(),
                latestEvent == null ? null : latestEvent.type(), latestEvent == null ? null : latestEvent.outcome(),
                journey.startedAt(), journey.lastEventAt(), journey.endedAt(),
                operations, decisions, events, explanation);
    }

    private JourneyExplanation explain(String journeyStatus,
                                       List<JourneyReadRepository.OperationFact> operations,
                                       List<JourneyReadRepository.DecisionFact> decisions,
                                       List<JourneyReadRepository.EventFact> events) {
        JourneyReadRepository.DecisionFact latestDecision = decisions.isEmpty()
                ? null : decisions.get(decisions.size() - 1);
        JourneyReadRepository.EventFact latestEvent = events.isEmpty()
                ? null : events.get(events.size() - 1);

        if (latestDecision != null && !"ALLOW".equalsIgnoreCase(latestDecision.decision())) {
            String cause = reasonText(latestDecision.reasons());
            String action = readableAction(latestDecision.action());
            String outcome = "REVIEW".equalsIgnoreCase(latestDecision.decision()) ? "REVIEW" : "BLOCKED";
            String statusText = operationStatus(operations, latestDecision.operationId());
            String noAction = "NOT_APPLIED".equalsIgnoreCase(statusText)
                    ? " The merchant did not apply this operation."
                    : " No successful merchant result is recorded for this operation.";
            String summary = "Guard " + ("REVIEW".equalsIgnoreCase(latestDecision.decision()) ? "requested review of " : "blocked ")
                    + action + ". " + cause + noAction;
            return new JourneyExplanation(outcome, summary, cause,
                    "Review the stated reason. If the shopper wants to continue, update the cart or obtain fresh approval as needed.",
                    latestDecision.operationId(), latestDecision.decisionId());
        }

        if (latestEvent != null && "CART_ACTION_CHECK_FAILED".equalsIgnoreCase(latestEvent.type())) {
            String cause = firstText(latestEvent.details().path("errorMessage").asText(null),
                    "AgentGuard could not complete the check; no allow decision was recorded.");
            return new JourneyExplanation("CHECK_FAILED",
                    "The cart action could not be checked. " + cause,
                    cause, "Check that AgentGuard is available, then retry the cart action.",
                    latestEvent.correlationId(), null);
        }

        if (latestEvent != null && ("FAILED".equalsIgnoreCase(latestEvent.outcome())
                || latestEvent.type().endsWith("_FAILED"))) {
            JsonNode resultDetails = latestEvent.details().path("resultDetails");
            String failureCode = firstText(resultDetails.path("failureCode").asText(null),
                    latestEvent.details().path("failureCode").asText(null));
            String message = firstText(resultDetails.path("message").asText(null),
                    latestEvent.details().path("errorMessage").asText(null));
            if ("SHOPPER_DECLINED".equalsIgnoreCase(failureCode)) {
                String declineMessage = firstText(message, "The shopper declined checkout.");
                return new JourneyExplanation("DECLINED",
                        "Checkout was declined by the shopper. " + declineMessage,
                        declineMessage, "No payment was attempted. Continue only if the shopper chooses to review checkout again.",
                        latestEvent.correlationId(), latestDecision == null ? null : latestDecision.decisionId());
            }
            String cause = joinCause(failureCode, message);
            if (cause.isBlank()) cause = "The merchant reported that the action failed, but did not save a more specific cause.";
            return new JourneyExplanation("FAILED",
                    "The merchant reported that " + readableEventAction(latestEvent.type()) + " failed. " + cause,
                    cause, "Check the merchant's current cart/order state before retrying, to avoid repeating an action that may have succeeded elsewhere.",
                    latestEvent.correlationId(), latestDecision == null ? null : latestDecision.decisionId());
        }

        if (latestEvent != null && "PAYMENT_SUCCEEDED".equalsIgnoreCase(latestEvent.type())) {
            return new JourneyExplanation("COMPLETED",
                    "Payment succeeded and the merchant reported the purchase as complete.",
                    "The latest merchant event is PAYMENT_SUCCEEDED.",
                    "No action is needed for this journey.", latestEvent.correlationId(),
                    latestDecision == null ? null : latestDecision.decisionId());
        }

        if (latestDecision != null && "ALLOW".equalsIgnoreCase(latestDecision.decision())) {
            String status = operationStatus(operations, latestDecision.operationId());
            if ("TIMED_OUT".equalsIgnoreCase(status)) {
                String cause = "The merchant did not report the result before the callback deadline.";
                return new JourneyExplanation("NEEDS_RECONCILIATION",
                        "Guard allowed the action, but the merchant result was not received in time. " + cause,
                        cause, "Check the merchant cart/order and payment provider before retrying.",
                        latestDecision.operationId(), latestDecision.decisionId());
            }
            if ("AWAITING_MERCHANT_RESULT".equalsIgnoreCase(status)) {
                return new JourneyExplanation("WAITING_FOR_MERCHANT",
                        "Guard allowed the action. Mandate Lens is waiting for the merchant to report whether it succeeded.",
                        "The operation is still awaiting the merchant result callback.",
                        "Wait for the merchant result before treating the cart change or payment as complete.",
                        latestDecision.operationId(), latestDecision.decisionId());
            }
        }

        String summary = "This journey is " + readableStatus(journeyStatus).toLowerCase()
                + ". The saved activity does not identify a specific failure.";
        return new JourneyExplanation(journeyStatus, summary,
                "No specific failure cause is present in the saved journey facts.",
                "Review the activity timeline for the latest merchant and Guard events.", null, null);
    }

    private String reasonText(JsonNode reasons) {
        if (reasons != null && reasons.isArray()) {
            List<String> messages = new java.util.ArrayList<>();
            reasons.forEach(reason -> {
                String message = firstText(reason.path("message").asText(null), reason.path("code").asText(null));
                if (!message.isBlank()) messages.add(message);
            });
            if (!messages.isEmpty()) return String.join("; ", messages);
        }
        return "AgentGuard returned a non-ALLOW decision but did not provide a readable reason.";
    }

    private String operationStatus(List<JourneyReadRepository.OperationFact> operations, String operationId) {
        if (operationId == null) return null;
        return operations.stream().filter(operationId::equals)
                .map(JourneyReadRepository.OperationFact::status).findFirst().orElse(null);
    }

    private String readableAction(String action) {
        if (action == null || action.isBlank()) return "the requested cart action";
        return action.toLowerCase().replace('_', ' ');
    }

    private String readableEventAction(String eventType) {
        if (eventType == null || eventType.isBlank()) return "the merchant action";
        return eventType.replaceFirst("(?i)_FAILED$", "").toLowerCase().replace('_', ' ');
    }

    private String readableStatus(String status) {
        return status == null || status.isBlank() ? "in progress" : status.replace('_', ' ');
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        return second == null ? "" : second.trim();
    }

    private String joinCause(String code, String message) {
        if (code == null || code.isBlank()) return message == null ? "" : message;
        if (message == null || message.isBlank() || message.equalsIgnoreCase(code)) return code;
        return code + ": " + message;
    }

    public List<JourneyReadRepository.JourneyListItem> listJourneys(String merchantId, String status, String search) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalizedStatus != null && !List.of("ACTIVE", "COMPLETED", "BLOCKED", "FAILED", "ABANDONED", "EXPIRED")
                .contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported journey status.");
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return repository.findJourneys(merchantId, normalizedStatus, normalizedSearch, 100);
    }

    public record JourneyDetail(String journeyId, String merchantId, String cartId, String status,
                                String latestEventType, String latestOutcome, Instant startedAt,
                                Instant lastEventAt, Instant endedAt,
                                List<JourneyReadRepository.OperationFact> operations,
                                List<JourneyReadRepository.DecisionFact> decisions,
                                List<JourneyReadRepository.EventFact> timeline,
                                JourneyExplanation explanation) { }

    public record JourneyExplanation(String outcome, String summary, String cause, String nextStep,
                                    String operationId, String decisionId) { }
}
