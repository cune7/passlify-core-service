package com.passlify.core.scan;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.Event;
import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.issuance.TicketStatus;
import com.passlify.core.issuance.qr.QrTokenService;
import com.passlify.core.scan.dto.ScanResponse;
import com.passlify.core.scan.dto.ScanSummaryResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry validation (DOMAIN §4.7). Inside one transaction: verify the QR signature,
 * lock the ticket row, check event/status, and flip {@code VALID → USED}. The
 * pessimistic lock makes concurrent scans of the same ticket safe — only the first
 * wins, double entry is blocked. Every attempt (allowed or denied) is audited.
 */
@Service
public class ScanService {

    private final TicketRepository tickets;
    private final TicketScanRepository scans;
    private final QrTokenService qrTokenService;
    private final ScanValidator validator;

    public ScanService(TicketRepository tickets, TicketScanRepository scans,
                       QrTokenService qrTokenService, ScanValidator validator) {
        this.tickets = tickets;
        this.scans = scans;
        this.qrTokenService = qrTokenService;
        this.validator = validator;
    }

    @Transactional
    public ScanResponse scan(String qrToken, UUID eventId, String gate, String operator) {
        Event event = validator.requireEvent(eventId);

        UUID ticketId;
        try {
            ticketId = qrTokenService.verify(qrToken).ticketId();
        } catch (ApiException e) {
            if (e.getCode() == ErrorCode.BAD_SIGNATURE) {
                record(null, event, ScanResult.DENIED, ScanDenyReason.BAD_SIGNATURE, gate, operator);
                return ScanResponse.denied(ScanDenyReason.BAD_SIGNATURE, null, null);
            }
            throw e;
        }

        // Lock the row so concurrent scans serialize on this ticket.
        Ticket ticket = tickets.findByIdForUpdate(ticketId).orElse(null);
        if (ticket == null) {
            record(null, event, ScanResult.DENIED, ScanDenyReason.NOT_FOUND, gate, operator);
            return ScanResponse.denied(ScanDenyReason.NOT_FOUND, null, null);
        }
        if (!ticket.getEvent().getId().equals(eventId)) {
            record(ticket, event, ScanResult.DENIED, ScanDenyReason.WRONG_EVENT, gate, operator);
            return ScanResponse.denied(ScanDenyReason.WRONG_EVENT, ticket, null);
        }

        switch (ticket.getStatus()) {
            case VOID -> {
                record(ticket, event, ScanResult.DENIED, ScanDenyReason.VOID, gate, operator);
                return ScanResponse.denied(ScanDenyReason.VOID, ticket, null);
            }
            case USED -> {
                Instant firstUsedAt = ticket.getUsedAt();
                record(ticket, event, ScanResult.DENIED, ScanDenyReason.ALREADY_USED, gate, operator);
                return ScanResponse.denied(ScanDenyReason.ALREADY_USED, ticket, firstUsedAt);
            }
            case VALID -> {
                ticket.setStatus(TicketStatus.USED);
                ticket.setUsedAt(Instant.now());
                ticket.setScanCount(ticket.getScanCount() + 1);
                record(ticket, event, ScanResult.ALLOWED, null, gate, operator);
                return ScanResponse.allowed(ticket);
            }
            default -> throw ApiException.invalidState("Unhandled ticket status: " + ticket.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public ScanSummaryResponse summary(UUID eventId) {
        validator.requireEvent(eventId);
        return new ScanSummaryResponse(
                tickets.countByEventId(eventId),
                tickets.countByEventIdAndStatus(eventId, TicketStatus.VALID),
                tickets.countByEventIdAndStatus(eventId, TicketStatus.USED),
                tickets.countByEventIdAndStatus(eventId, TicketStatus.VOID));
    }

    private void record(Ticket ticket, Event event, ScanResult result,
                        ScanDenyReason reason, String gate, String operator) {
        TicketScan scan = new TicketScan();
        scan.setTicket(ticket);
        scan.setEvent(event);
        scan.setResult(result);
        scan.setReason(reason);
        scan.setGate(gate);
        scan.setScannedBy(operator);
        scans.save(scan);
    }
}
