package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Offline / bank-transfer / admin-confirmed payments (EVENT_DOMAIN_SPEC §10). No
 * external processor: instead of a redirect, {@link #createSession} points the buyer at
 * a payment-instructions page (bank account + amount + reference). The payment is later
 * confirmed by the event organizer/manager, not by a webhook — so {@link #verifyAndParse}
 * is never used. Always available (no credentials), like the mock gateway.
 */
@Component
public class ManualPaymentGateway implements PaymentGateway {

    public static final String INSTRUCTIONS_PATH = "/api/v1/public/payments/manual/instructions/";

    private final String baseUrl;

    public ManualPaymentGateway(@Value("${passlify.base-url:http://localhost:8081}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MANUAL;
    }

    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        String reference = order.getId().toString();
        return new CheckoutSession(reference, baseUrl + INSTRUCTIONS_PATH + reference, null);
    }

    @Override
    public PaymentEvent verifyAndParse(String rawBody, String signature) {
        throw ApiException.invalidState(
                "Manual payments are confirmed by the organizer, not via webhook");
    }
}
