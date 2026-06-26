package com.passlify.core.payment;

import com.passlify.core.payment.dto.PaymentSessionRequest;
import com.passlify.core.payment.dto.PaymentSessionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creates a hosted checkout session for an order. Guest-allowed (the order id is
 * the capability) — open in SecurityConfig. The client redirects the buyer to
 * {@code checkoutUrl}.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/orders/{id}/payment-session")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentSessionResponse createSession(@PathVariable UUID id,
                                                @Valid @RequestBody(required = false) PaymentSessionRequest req) {
        String successUrl = req == null ? null : req.successUrl();
        String cancelUrl = req == null ? null : req.cancelUrl();
        return paymentService.createSession(id, successUrl, cancelUrl);
    }
}
