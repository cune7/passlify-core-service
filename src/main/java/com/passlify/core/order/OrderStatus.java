package com.passlify.core.order;

/**
 * Order lifecycle. MVP checkout creates {@code PENDING_PAYMENT}; payment moves it
 * to {@code PAID}/{@code FAILED}; the reservation expirer moves stale ones to
 * {@code EXPIRED}; refunds (later slice) use the REFUNDED states.
 */
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
