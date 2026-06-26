package com.passlify.core.ticket.dto;

import com.passlify.core.ticket.AttendeeDataMode;
import com.passlify.core.ticket.TicketKind;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeVisibility;
import java.time.Instant;
import java.util.UUID;

/** Organizer-facing ticket-type view (includes derived {@code availableQuantity}). */
public record TicketTypeResponse(
        UUID id,
        UUID eventId,
        String name,
        String description,
        long priceMinor,
        String currency,
        int totalQuantity,
        int soldQuantity,
        int availableQuantity,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        boolean active,
        TicketTypeVisibility visibility,
        TicketKind kind,
        AttendeeDataMode attendeeDataMode,
        int sortOrder) {

    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(
                t.getId(),
                t.getEvent().getId(),
                t.getName(),
                t.getDescription(),
                t.getPriceMinor(),
                t.getCurrency(),
                t.getTotalQuantity(),
                t.getSoldQuantity(),
                t.availableQuantity(),
                t.getMaxPerOrder(),
                t.getSalesStartAt(),
                t.getSalesEndAt(),
                t.isActive(),
                t.getVisibility(),
                t.getKind(),
                t.getAttendeeDataMode(),
                t.getSortOrder());
    }
}
