package com.passlify.core.issuance;

/** Issued-ticket lifecycle. Scan flips {@code VALID → USED}; refunds set {@code VOID}. */
public enum TicketStatus {
    VALID,
    USED,
    VOID
}
