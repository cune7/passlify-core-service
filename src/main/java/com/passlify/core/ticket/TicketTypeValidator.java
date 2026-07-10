package com.passlify.core.ticket;

import com.passlify.core.common.error.ApiException;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Ticket-type precondition checks: sales-window sanity, and the inventory guards
 * (cannot reduce total below sold, cannot delete a type with sold tickets).
 * Injected into {@link TicketTypeService}.
 */
@Component
public class TicketTypeValidator {

    public void validateSalesWindow(Instant start, Instant end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw ApiException.validation("salesEndAt must be after salesStartAt");
        }
    }

    /** Total quantity may never drop below what has already been sold. */
    public void assertCanSetTotalQuantity(TicketType t, int newTotal) {
        if (newTotal < t.getSoldQuantity()) {
            throw ApiException.conflict(
                    "Cannot reduce total quantity below sold quantity (" + t.getSoldQuantity() + ")");
        }
    }

    public void assertDeletable(TicketType t) {
        if (t.getSoldQuantity() > 0) {
            throw ApiException.conflict("Cannot delete a ticket type that has sold tickets");
        }
    }
}
