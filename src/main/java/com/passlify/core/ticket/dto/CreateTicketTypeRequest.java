package com.passlify.core.ticket.dto;

import com.passlify.core.ticket.TicketKind;
import com.passlify.core.ticket.TicketTypeVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Create a ticket type within an event. {@code currency} defaults to the event's
 * currency; {@code priceMinor} is in integer minor units and is authoritative.
 */
public record CreateTicketTypeRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @NotNull @PositiveOrZero Long priceMinor,
        @Size(min = 3, max = 3) String currency,
        @NotNull @Positive Integer totalQuantity,
        @Positive Integer maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        Boolean active,
        TicketTypeVisibility visibility,
        TicketKind kind,
        com.passlify.core.ticket.AttendeeDataMode attendeeDataMode,
        Integer sortOrder) {
}
