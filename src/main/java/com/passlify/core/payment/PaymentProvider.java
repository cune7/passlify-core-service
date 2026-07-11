package com.passlify.core.payment;

/**
 * Which payment adapter handles an event's checkout. The organizer selects this
 * per event (EVENT_DOMAIN_SPEC §10).
 *
 * <ul>
 *   <li>{@code NONE} — free event; no payment processing.</li>
 *   <li>{@code MOCK} — fully simulates the pay flow (MVP + automated tests).</li>
 *   <li>{@code MANUAL} — bank-transfer / cash / admin-confirmed reconciliation.</li>
 *   <li>{@code RAIFFEISEN} — Serbian Raiffeisen e-commerce card integration (RSD).</li>
 *   <li>{@code STRIPE} — Stripe Checkout / Payment Intents.</li>
 * </ul>
 *
 * Real processors plug in as new adapter implementations; the organizer may only
 * select a provider that has an active, admin-approved capability (later phase).
 */
public enum PaymentProvider {
    NONE,
    MOCK,
    MANUAL,
    RAIFFEISEN,
    STRIPE;

    /**
     * Real external money processors require an admin-approved capability before a paid
     * event may publish on them. {@code NONE}/{@code MOCK} are platform built-ins and
     * {@code MANUAL} is offline/admin-confirmed, so none of those need approval.
     */
    public boolean requiresCapability() {
        return this == STRIPE || this == RAIFFEISEN;
    }
}
