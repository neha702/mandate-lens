package com.emeralds.mandatelens.agentguard.client;

import com.emeralds.mandatelens.agentguard.model.EvaluateCartRequest;
import com.emeralds.mandatelens.agentguard.model.EvaluateCheckoutRequest;
import com.emeralds.mandatelens.agentguard.model.GuardDecisionResponse;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateRequest;
import com.emeralds.mandatelens.agentguard.model.RegisterMandateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/** Contract adapter. Calls are loopback-only until AgentGuard authentication is specified. */
@Component
@Profile("!agentguard-deterministic & !storesync-sandbox-test")
public class HttpAgentGuardClient implements AgentGuardClient {
    private final String baseUrl;
    private final RestClient client;

    public HttpAgentGuardClient(@Value("${agentguard.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.client = RestClient.builder().baseUrl(this.baseUrl.isBlank() ? "http://127.0.0.1" : this.baseUrl).build();
    }

    @Override
    public RegisterMandateResponse registerMandate(RegisterMandateRequest request) {
        return post("/api/guard/mandates", request, RegisterMandateResponse.class);
    }

    @Override
    public GuardDecisionResponse evaluateCart(EvaluateCartRequest request) {
        return post("/api/guard/evaluate/cart", request, GuardDecisionResponse.class);
    }

    @Override
    public GuardDecisionResponse evaluateCheckout(EvaluateCheckoutRequest request) {
        return post("/api/guard/evaluate/checkout", request, GuardDecisionResponse.class);
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        requireLocalConfiguration();
        try {
            T response = client.post().uri(path).body(request).retrieve().body(responseType);
            if (response == null) throw new AgentGuardUnavailableException("AgentGuard returned an empty response.");
            return response;
        } catch (RestClientException exception) {
            throw new AgentGuardUnavailableException("AgentGuard request failed.", exception);
        }
    }

    private void requireLocalConfiguration() {
        if (baseUrl.isBlank()) throw new AgentGuardUnavailableException("AgentGuard is not configured.");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new AgentGuardUnavailableException("AgentGuard URL is invalid.", exception);
        }
        String host = uri.getHost();
        boolean loopback = host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"));
        if (!loopback) throw new AgentGuardUnavailableException(
                "AgentGuard calls are limited to loopback until its authentication contract is confirmed.");
    }

    public static class AgentGuardUnavailableException extends RuntimeException {
        public AgentGuardUnavailableException(String message) { super(message); }
        public AgentGuardUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
