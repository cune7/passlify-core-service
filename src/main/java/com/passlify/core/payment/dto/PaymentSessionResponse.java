package com.passlify.core.payment.dto;

import java.util.UUID;

/** Where to send the buyer to pay, plus the persisted payment + provider session ids. */
public record PaymentSessionResponse(UUID paymentId, String checkoutUrl, String sessionId) {
}
