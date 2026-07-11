package com.passlify.core.payment.gateway;

import com.passlify.core.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook endpoint. Separate from the generic {@code /webhooks/{provider}} sink
 * because Stripe's signature travels in the {@code Stripe-Signature} header (not
 * {@code X-Signature}). Reads the raw body for signature verification and delegates to
 * {@link PaymentService}. Only present when Stripe is enabled.
 */
@RestController
@ConditionalOnProperty(prefix = "passlify.stripe", name = "enabled", havingValue = "true")
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/webhooks/stripe")
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleWebhook("stripe", rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
