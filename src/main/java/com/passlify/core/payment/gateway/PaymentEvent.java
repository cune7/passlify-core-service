package com.passlify.core.payment.gateway;

/**
 * A provider webhook normalized to provider-agnostic terms. {@code eventId} is the
 * idempotency key (deduped in the ledger). {@code IGNORED} events are recorded and
 * ACKed without side effects.
 */
public record PaymentEvent(
        String eventId,
        Type type,
        String sessionId,
        String intentId,
        String chargeId,
        Long refundedMinor) {

    public enum Type {
        PAID,
        FAILED,
        REFUNDED,
        IGNORED
    }
}
