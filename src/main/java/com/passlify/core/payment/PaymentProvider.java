package com.passlify.core.payment;

/**
 * Which payment adapter handles an event's checkout. The organizer selects this
 * per event. {@code MOCK} fully simulates the pay flow (used for the MVP and for
 * free/RSD orders without an external account); {@code MANUAL} is bank-transfer /
 * admin-confirmed; real processors (STRIPE, and later a Serbian gateway) plug in
 * as new adapter implementations + a migration widening the DB check constraint.
 */
public enum PaymentProvider {
    MOCK,
    MANUAL,
    STRIPE
}
