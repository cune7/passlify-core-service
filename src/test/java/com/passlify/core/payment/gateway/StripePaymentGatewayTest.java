package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the Stripe webhook signature check + event mapping using locally
 * Stripe-signed payloads (no live Stripe). createSession needs the live API and is
 * covered by manual test-mode verification.
 */
class StripePaymentGatewayTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private final StripePaymentGateway gateway =
            new StripePaymentGateway("sk_test_x", WEBHOOK_SECRET, JsonMapper.builder().build());

    @Test
    void checkoutCompletedMapsToPaid() {
        String payload = event("checkout.session.completed",
                "{\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"payment_intent\":\"pi_test_1\"}");
        PaymentEvent e = gateway.verifyAndParse(payload, sign(payload));
        assertThat(e.type()).isEqualTo(PaymentEvent.Type.PAID);
        assertThat(e.sessionId()).isEqualTo("cs_test_1");
        assertThat(e.intentId()).isEqualTo("pi_test_1");
    }

    @Test
    void paymentIntentFailedMapsToFailed() {
        String payload = event("payment_intent.payment_failed",
                "{\"id\":\"pi_test_2\",\"object\":\"payment_intent\"}");
        PaymentEvent e = gateway.verifyAndParse(payload, sign(payload));
        assertThat(e.type()).isEqualTo(PaymentEvent.Type.FAILED);
        assertThat(e.intentId()).isEqualTo("pi_test_2");
    }

    @Test
    void chargeRefundedCarriesRefundedAmount() {
        String payload = event("charge.refunded",
                "{\"id\":\"ch_test_3\",\"object\":\"charge\",\"payment_intent\":\"pi_3\",\"amount_refunded\":1500}");
        PaymentEvent e = gateway.verifyAndParse(payload, sign(payload));
        assertThat(e.type()).isEqualTo(PaymentEvent.Type.REFUNDED);
        assertThat(e.chargeId()).isEqualTo("ch_test_3");
        assertThat(e.refundedMinor()).isEqualTo(1500L);
    }

    @Test
    void unknownEventIsIgnored() {
        String payload = event("customer.created", "{\"id\":\"cus_x\"}");
        assertThat(gateway.verifyAndParse(payload, sign(payload)).type()).isEqualTo(PaymentEvent.Type.IGNORED);
    }

    @Test
    void badSignatureIsRejected() {
        String payload = event("checkout.session.completed", "{\"id\":\"cs_x\"}");
        assertThatThrownBy(() -> gateway.verifyAndParse(payload, "t=1,v1=deadbeef"))
                .isInstanceOf(ApiException.class);
    }

    private static String event(String type, String dataObject) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"2024-06-20\",\"type\":\""
                + type + "\",\"data\":{\"object\":" + dataObject + "}}";
    }

    /** Builds a valid Stripe-Signature header: t=<ts>,v1=hex(HMAC-SHA256(ts + "." + payload)). */
    private static String sign(String payload) {
        long ts = System.currentTimeMillis() / 1000; // within Stripe's default 300s tolerance
        String signed = ts + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + ts + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
