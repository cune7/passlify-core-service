package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stripe Checkout gateway (EVENT_DOMAIN_SPEC §10). {@link #createSession} creates a
 * hosted Checkout Session; {@link #verifyAndParse} verifies the {@code Stripe-Signature}
 * with the webhook secret and normalizes the event.
 *
 * <p>Activated only when {@code passlify.stripe.enabled=true} + a secret key, so the app
 * runs without it in dev/test. Event fields are read from the raw event JSON (not the
 * SDK model) so mapping is independent of the account's API version.
 *
 * <p><b>Needs live verification:</b> exercise against Stripe test-mode keys — the
 * Checkout Session params and webhook mapping are per the SDK docs but not run here.
 */
@Component
@ConditionalOnProperty(prefix = "passlify.stripe", name = "enabled", havingValue = "true")
public class StripePaymentGateway implements PaymentGateway {

    private final String secretKey;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public StripePaymentGateway(@Value("${passlify.stripe.secret-key}") String secretKey,
                                @Value("${passlify.stripe.webhook-secret}") String webhookSecret,
                                ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomerEmail(order.getCustomerEmail())
                .putMetadata("orderId", order.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(order.getCurrency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(order.getTotalMinor())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Order " + order.getId())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params,
                    RequestOptions.builder().setApiKey(secretKey).build());
            return new CheckoutSession(session.getId(), session.getUrl(), session.getPaymentIntent());
        } catch (StripeException e) {
            throw ApiException.of(ErrorCode.INVALID_STATE, "Stripe checkout session failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentEvent verifyAndParse(String rawBody, String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(rawBody, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw ApiException.of(ErrorCode.BAD_SIGNATURE, "Invalid Stripe webhook signature");
        }
        JsonNode obj = dataObject(event);
        String type = event.getType();
        return switch (type) {
            case "checkout.session.completed" -> new PaymentEvent(event.getId(), PaymentEvent.Type.PAID,
                    text(obj, "id"), text(obj, "payment_intent"), null, null);
            case "payment_intent.succeeded" -> new PaymentEvent(event.getId(), PaymentEvent.Type.PAID,
                    null, text(obj, "id"), null, null);
            case "payment_intent.payment_failed" -> new PaymentEvent(event.getId(), PaymentEvent.Type.FAILED,
                    null, text(obj, "id"), null, null);
            case "charge.refunded" -> new PaymentEvent(event.getId(), PaymentEvent.Type.REFUNDED,
                    null, text(obj, "payment_intent"), text(obj, "id"), amount(obj, "amount_refunded"));
            default -> new PaymentEvent(event.getId(), PaymentEvent.Type.IGNORED, null, null, null, null);
        };
    }

    @Override
    public void refund(com.passlify.core.payment.Payment payment, long amountMinor) {
        try {
            com.stripe.model.Refund.create(
                    com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(payment.getProviderIntentId())
                            .setAmount(amountMinor)
                            .build(),
                    RequestOptions.builder().setApiKey(secretKey).build());
        } catch (StripeException e) {
            throw ApiException.of(ErrorCode.INVALID_STATE, "Stripe refund failed: " + e.getMessage());
        }
    }

    /** The event's {@code data.object} as JSON — version-independent field access. */
    private JsonNode dataObject(Event event) {
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        try {
            return objectMapper.readTree(rawJson == null ? "{}" : rawJson);
        } catch (RuntimeException e) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR, "Unreadable Stripe event payload");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static Long amount(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }
}
