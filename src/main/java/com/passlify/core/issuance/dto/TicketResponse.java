package com.passlify.core.issuance.dto;

import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/** Buyer-facing ticket view. {@code qrUrl}/{@code pdfUrl} are relative API paths. */
public record TicketResponse(
        UUID id,
        String serialNumber,
        TicketStatus status,
        UUID eventId,
        String eventName,
        Instant startsAt,
        String ticketTypeName,
        String attendeeName,
        String ownerEmail,
        Instant issuedAt,
        Instant usedAt,
        String qrUrl,
        String pdfUrl) {

    public static TicketResponse from(Ticket t) {
        return new TicketResponse(
                t.getId(),
                t.getSerialNumber(),
                t.getStatus(),
                t.getEvent().getId(),
                t.getEvent().getName(),
                t.getEvent().getStartsAt(),
                t.getTicketType().getName(),
                t.getAttendeeName(),
                t.getOwnerEmail(),
                t.getIssuedAt(),
                t.getUsedAt(),
                "/api/v1/tickets/" + t.getId() + "/qr",
                "/api/v1/tickets/" + t.getId() + "/pdf");
    }
}
