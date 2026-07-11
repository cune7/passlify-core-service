package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.payment.Payment;
import com.passlify.core.payment.PaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redirect page for the Raiffeisen/UPC flow: the buyer's browser lands here
 * after checkout and is auto-POSTed to the bank's secure page with the signed request
 * fields (the gateway requires a form POST, not a GET). Only present when Raiffeisen
 * is enabled.
 */
@RestController
@ConditionalOnProperty(prefix = "passlify.raiffeisen", name = "enabled", havingValue = "true")
public class RaiffeisenRedirectController {

    private final RaiffeisenPaymentGateway gateway;
    private final PaymentRepository payments;

    public RaiffeisenRedirectController(RaiffeisenPaymentGateway gateway, PaymentRepository payments) {
        this.gateway = gateway;
        this.payments = payments;
    }

    @GetMapping(value = RaiffeisenPaymentGateway.REDIRECT_PATH + "{orderRef}",
            produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String redirect(@PathVariable String orderRef) {
        Payment payment = payments.findByProviderSessionId(orderRef)
                .orElseThrow(() -> ApiException.notFound("Unknown payment session: " + orderRef));
        return gateway.renderRedirectForm(payment.getOrder());
    }
}
