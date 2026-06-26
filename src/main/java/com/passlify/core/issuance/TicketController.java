package com.passlify.core.issuance;

import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.issuance.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket retrieval. Per-ticket and per-order reads are guest-allowed (the UUID is
 * the capability — open in SecurityConfig); {@code /me/tickets} is for the
 * authenticated buyer.
 */
@RestController
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUser currentUser;

    public TicketController(TicketService ticketService, CurrentUser currentUser) {
        this.ticketService = ticketService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/orders/{orderId}/tickets")
    public List<TicketResponse> forOrder(@PathVariable UUID orderId) {
        return ticketService.listForOrder(orderId).stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/api/v1/tickets/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.get(id));
    }

    @GetMapping("/api/v1/tickets/{id}/qr")
    public ResponseEntity<byte[]> qr(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(ticketService.qrPng(id));
    }

    @GetMapping("/api/v1/tickets/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(ticketService.pdf(id));
    }

    @GetMapping("/api/v1/me/tickets")
    @PreAuthorize("isAuthenticated()")
    public Page<TicketResponse> mine(@RequestParam(required = false) UUID eventId,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return ticketService.listForCustomer(currentUser.requireSubject(), eventId, pageable)
                .map(TicketResponse::from);
    }
}
