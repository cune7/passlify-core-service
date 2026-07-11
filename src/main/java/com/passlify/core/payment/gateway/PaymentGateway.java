package com.passlify.core.payment.gateway;

import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;

/**
 * A payment processor adapter. The organizer picks a {@link PaymentProvider} per
 * event; the matching gateway creates the hosted checkout and normalizes inbound
 * webhooks into a {@link PaymentEvent}. New processors (Stripe, a Serbian gateway)
 * are added by implementing this — checkout/webhook flow code stays unchanged.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Creates a hosted checkout session for the order and returns where to send the buyer. */
    CheckoutSession createSession(Order order, String successUrl, String cancelUrl);

    /**
     * Verifies authenticity of a raw webhook body and parses it into a normalized
     * event. Implementations must throw an {@code ApiException(BAD_SIGNATURE)} when
     * verification fails.
     */
    PaymentEvent verifyAndParse(String rawBody, String signature);

    /**
     * Moves money back to the buyer for an organizer-initiated refund of
     * {@code amountMinor}. Default is a no-op — MANUAL/MOCK settle offline and the
     * internal refund state is applied by the caller. Real processors (Stripe) override
     * this to call their refund API.
     */
    default void refund(com.passlify.core.payment.Payment payment, long amountMinor) {
        // no external processor
    }
}
