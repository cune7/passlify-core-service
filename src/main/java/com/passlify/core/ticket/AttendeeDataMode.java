package com.passlify.core.ticket;

/**
 * Whether per-attendee data is collected for a ticket type (master spec §6.7).
 * {@code BUYER_ONLY}: all tickets go to the buyer (ticket uses buyer info).
 * {@code EACH_TICKET}: separate attendee data is collected per ticket.
 * OPTIONAL_EACH_TICKET / SAME_AS_BUYER_ALLOWED are deferred.
 */
public enum AttendeeDataMode {
    BUYER_ONLY,
    EACH_TICKET
}
