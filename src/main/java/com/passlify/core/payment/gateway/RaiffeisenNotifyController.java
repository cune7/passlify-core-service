package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raiffeisen/UPC NOTIFY_URL endpoint (§7). The gateway posts the transaction result
 * server-to-server and expects a {@code Response.action} answer in the body — without
 * {@code approve} it auto-reverses the payment. We verify + record the event, then
 * approve a successful auth (reverse on bad signature / failure). Only present when
 * Raiffeisen is enabled.
 */
@RestController
@ConditionalOnProperty(prefix = "passlify.raiffeisen", name = "enabled", havingValue = "true")
public class RaiffeisenNotifyController {

    private final RaiffeisenPaymentGateway gateway;
    private final PaymentService paymentService;

    public RaiffeisenNotifyController(RaiffeisenPaymentGateway gateway, PaymentService paymentService) {
        this.gateway = gateway;
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/api/v1/webhooks/raiffeisen/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestBody(required = false) String body) {
        boolean approve;
        try {
            paymentService.handleWebhook("raiffeisen", body, null); // verifies signature + records/processes
            approve = gateway.isSuccessful(body);
        } catch (ApiException e) {
            approve = false; // bad signature or processing error → do not accept the transaction
        }
        return gateway.buildNotifyResponse(body, approve);
    }
}
