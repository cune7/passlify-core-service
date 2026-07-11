package com.passlify.core.event;

/**
 * Whether an event is commercial. Stored explicitly rather than inferred from
 * ticket prices (EVENT_DOMAIN_SPEC §9): commercial rules must be enforceable
 * before any ticket type exists.
 *
 * <ul>
 *   <li>{@code FREE} — all ticket types must be priced 0; payment provider is
 *       {@link com.passlify.core.payment.PaymentProvider#NONE}; no external
 *       payment session is created.</li>
 *   <li>{@code PAID} — requires a complete {@code COMPANY} organization and an
 *       approved payment provider before publication.</li>
 * </ul>
 *
 * A future {@code MIXED} mode may allow complimentary tickets alongside paid.
 */
public enum CommercialMode {
    FREE,
    PAID
}
