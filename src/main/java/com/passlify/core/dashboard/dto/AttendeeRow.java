package com.passlify.core.dashboard.dto;

import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/** One attendee = one issued ticket. */
public record AttendeeRow(
        UUID ticketId,
        String serialNumber,
        String ticketTypeName,
        String attendeeName,
        String ownerEmail,
        TicketStatus status,
        Instant checkedInAt,
        UUID orderId) {

    public static AttendeeRow from(Ticket t) {
        return new AttendeeRow(
                t.getId(),
                t.getSerialNumber(),
                t.getTicketType().getName(),
                t.getAttendeeName(),
                t.getOwnerEmail(),
                t.getStatus(),
                t.getUsedAt(),
                t.getOrder().getId());
    }
}
