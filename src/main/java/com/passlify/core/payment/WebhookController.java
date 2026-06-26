package com.passlify.core.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider-agnostic webhook sink. Reads the RAW request body (needed for real
 * signature verification — never let Spring parse it to an object first) and the
 * provider's signature header, then hands off to {@link PaymentService} which
 * verifies, dedups and processes. Always ACKs 200 for accepted events; a bad
 * signature surfaces as 400 via the gateway.
 *
 * For the MOCK provider, POST e.g.
 * <pre>{ "type": "PAID", "sessionId": "mock_sess_..." }</pre>
 * to {@code /api/v1/webhooks/mock} to simulate the bank confirming/declining.
 */
@RestController
public class WebhookController {

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/webhooks/{provider}")
    public ResponseEntity<Void> receive(
            @PathVariable String provider,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        paymentService.handleWebhook(provider, rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
