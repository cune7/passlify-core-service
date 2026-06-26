package com.passlify.core.issuance;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.issuance.pdf.TicketPdfService;
import com.passlify.core.issuance.qr.QrImageService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Buyer-facing ticket reads + QR/PDF rendering. */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final QrImageService qrImageService;
    private final TicketPdfService ticketPdfService;

    public TicketService(TicketRepository tickets, QrImageService qrImageService, TicketPdfService ticketPdfService) {
        this.tickets = tickets;
        this.qrImageService = qrImageService;
        this.ticketPdfService = ticketPdfService;
    }

    @Transactional(readOnly = true)
    public Ticket get(UUID id) {
        return tickets.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ticket not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Ticket> listForOrder(UUID orderId) {
        return tickets.findByOrderIdOrderBySerialNumberAsc(orderId);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> listForCustomer(String customerId, UUID eventId, Pageable pageable) {
        return eventId == null
                ? tickets.findByOwnerCustomerId(customerId, pageable)
                : tickets.findByOwnerCustomerIdAndEventId(customerId, eventId, pageable);
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID id) {
        return qrImageService.pngFor(get(id).getQrToken());
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        return ticketPdfService.render(get(id));
    }
}
