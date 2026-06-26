package com.passlify.core.forms;

/**
 * Where a custom field is collected (master spec §5.9). {@code PER_PURCHASE} is
 * filled once for the buyer/order; {@code PER_ATTENDEE} is filled for every ticket
 * of an {@code EACH_TICKET} ticket type. PER_TICKET_TYPE / ADMIN_ONLY are deferred.
 */
public enum FieldScope {
    PER_PURCHASE,
    PER_ATTENDEE
}
