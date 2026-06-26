package com.passlify.core.payment;

/** Lifecycle of a single payment attempt against an order. */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
