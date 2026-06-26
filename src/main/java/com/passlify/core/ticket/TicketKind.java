package com.passlify.core.ticket;

/**
 * What kind of access a ticket grants. MVP issues {@code SINGLE_USE} only; the
 * others are modelled now so multi-entry passes slot in later without migration.
 */
public enum TicketKind {
    SINGLE_USE,
    MULTI_DAY_PASS,
    SEASON_PASS,
    MEMBERSHIP
}
