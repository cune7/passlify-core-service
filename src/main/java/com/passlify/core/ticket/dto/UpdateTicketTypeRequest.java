package com.passlify.core.ticket.dto;

import com.passlify.core.ticket.TicketKind;
import com.passlify.core.ticket.TicketTypeVisibility;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Partial update — only non-null fields are applied. */
public record UpdateTicketTypeRequest(
        @Size(max = 120) String name,
        String description,
        @PositiveOrZero Long priceMinor,
        @Size(min = 3, max = 3) String currency,
        @Positive Integer totalQuantity,
        @Positive Integer maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        Boolean active,
        TicketTypeVisibility visibility,
        TicketKind kind,
        com.passlify.core.ticket.AttendeeDataMode attendeeDataMode,
        Integer sortOrder) {
}
