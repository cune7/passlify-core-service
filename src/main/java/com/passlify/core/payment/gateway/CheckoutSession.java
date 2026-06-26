package com.passlify.core.payment.gateway;

/** Result of creating a hosted checkout: where to redirect the buyer + provider ids. */
public record CheckoutSession(String sessionId, String checkoutUrl, String intentId) {
}
