package com.passlify.core.scan.dto;

import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketStatus;
import com.passlify.core.scan.ScanDenyReason;
import com.passlify.core.scan.ScanResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Scan verdict. Always returned with HTTP 200 — a denial is a result, not an error,
 * which keeps the gate client simple.
 */
public record ScanResponse(ScanResult result, ScanDenyReason reason, TicketInfo ticket) {

    public record TicketInfo(
            UUID id,
            String serialNumber,
            String ticketTypeName,
            String attendeeName,
            TicketStatus status,
            Instant usedAt,
            Instant firstUsedAt) {
    }

    public static ScanResponse allowed(Ticket t) {
        return new ScanResponse(ScanResult.ALLOWED, null, info(t, null));
    }

    public static ScanResponse denied(ScanDenyReason reason, Ticket t, Instant firstUsedAt) {
        return new ScanResponse(ScanResult.DENIED, reason, t == null ? null : info(t, firstUsedAt));
    }

    private static TicketInfo info(Ticket t, Instant firstUsedAt) {
        return new TicketInfo(
                t.getId(),
                t.getSerialNumber(),
                t.getTicketType().getName(),
                t.getAttendeeName(),
                t.getStatus(),
                t.getUsedAt(),
                firstUsedAt);
    }
}
