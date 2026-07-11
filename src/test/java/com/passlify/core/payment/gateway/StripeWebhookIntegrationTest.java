package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.passlify.core.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Enables Stripe and drives the webhook endpoint through the real controller — verifying
 * the conditional beans wire, the {@code /webhooks/stripe} mapping doesn't clash with the
 * generic sink, and a signed event is verified + acknowledged.
 */
@AutoConfigureMockMvc
class StripeWebhookIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_it_secret";

    @DynamicPropertySource
    static void stripe(DynamicPropertyRegistry r) {
        r.add("passlify.stripe.enabled", () -> "true");
        r.add("passlify.stripe.secret-key", () -> "sk_test_x");
        r.add("passlify.stripe.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void signedWebhookIsAcceptedAndVerified() throws Exception {
        String payload = "{\"id\":\"evt_it\",\"object\":\"event\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"cs_it\",\"payment_intent\":\"pi_it\"}}}";
        mvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", sign(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void unsignedWebhookIsRejected() throws Exception {
        String payload = "{\"id\":\"evt_it2\",\"object\":\"event\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"cs_x\"}}}";
        mvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=bad")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    private static String sign(String payload) {
        long ts = System.currentTimeMillis() / 1000;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((ts + "." + payload).getBytes(StandardCharsets.UTF_8));
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
