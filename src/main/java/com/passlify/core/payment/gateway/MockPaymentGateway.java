package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fully simulated processor. {@link #createSession} mints fake session/intent ids
 * and a fake hosted-checkout URL; {@link #verifyAndParse} accepts a JSON body that
 * the caller POSTs to {@code /api/v1/webhooks/mock} to drive PAID/FAILED/REFUNDED —
 * this is the MVP's "simulate the bank" hook. No real signature; both outcomes are
 * exercisable end-to-end without any external account.
 *
 * Expected webhook body:
 * <pre>{ "type": "PAID|FAILED|REFUNDED", "sessionId": "mock_sess_...", "eventId": "optional" }</pre>
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public MockPaymentGateway(ObjectMapper objectMapper,
                              @Value("${passlify.base-url:http://localhost:8081}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        String sessionId = "mock_sess_" + UUID.randomUUID().toString().replace("-", "");
        String intentId = "mock_pi_" + UUID.randomUUID().toString().replace("-", "");
        String checkoutUrl = baseUrl + "/mock-checkout?session=" + sessionId;
        return new CheckoutSession(sessionId, checkoutUrl, intentId);
    }

    @Override
    public PaymentEvent verifyAndParse(String rawBody, String signature) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (Exception e) {
            throw ApiException.validation("Malformed mock webhook body");
        }
        if (node == null || !node.hasNonNull("type") || !node.hasNonNull("sessionId")) {
            throw ApiException.validation("Mock webhook requires 'type' and 'sessionId'");
        }

        String sessionId = node.get("sessionId").asText();
        PaymentEvent.Type type = parseType(node.get("type").asText());
        String intentId = node.hasNonNull("intentId") ? node.get("intentId").asText() : null;
        String chargeId = node.hasNonNull("chargeId") ? node.get("chargeId").asText() : null;
        // Optional partial-refund amount; absent ⇒ full refund.
        Long refundedMinor = node.hasNonNull("refundedMinor") ? node.get("refundedMinor").asLong() : null;

        // Deterministic id when none supplied → retries of the same simulated event dedup.
        String eventId = node.hasNonNull("eventId")
                ? node.get("eventId").asText()
                : "mock_evt_" + sessionId + "_" + type;

        return new PaymentEvent(eventId, type, sessionId, intentId, chargeId, refundedMinor);
    }

    private PaymentEvent.Type parseType(String raw) {
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "PAID", "SUCCEEDED", "COMPLETED" -> PaymentEvent.Type.PAID;
            case "FAILED" -> PaymentEvent.Type.FAILED;
            case "REFUNDED" -> PaymentEvent.Type.REFUNDED;
            default -> PaymentEvent.Type.IGNORED;
        };
    }
}
