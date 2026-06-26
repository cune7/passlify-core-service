package com.passlify.core.ticket;

/**
 * Whether a ticket type is shown on the public event page. {@code HIDDEN} types
 * (VIP/partner/press) are sold via direct link or admin issuance (master spec §6.6).
 * Promo-code-gated and password-protected variants are deferred.
 */
public enum TicketTypeVisibility {
    PUBLIC,
    HIDDEN
}
