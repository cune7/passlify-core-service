package com.passlify.core.ticket;

import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import com.passlify.core.ticket.dto.TicketTypeResponse;
import com.passlify.core.ticket.dto.UpdateTicketTypeRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket-type management. Create/list are nested under an event; update/delete
 * target a ticket type directly (matches API.md §2). Ownership in the service.
 */
@RestController
@PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    public TicketTypeController(TicketTypeService ticketTypeService) {
        this.ticketTypeService = ticketTypeService;
    }

    @PostMapping("/api/v1/events/{eventId}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@PathVariable UUID eventId,
                                     @Valid @RequestBody CreateTicketTypeRequest req) {
        return TicketTypeResponse.from(ticketTypeService.create(eventId, req));
    }

    @GetMapping("/api/v1/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> list(@PathVariable UUID eventId) {
        return ticketTypeService.listForEvent(eventId).stream()
                .map(TicketTypeResponse::from)
                .toList();
    }

    @PatchMapping("/api/v1/ticket-types/{id}")
    public TicketTypeResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateTicketTypeRequest req) {
        return TicketTypeResponse.from(ticketTypeService.update(id, req));
    }

    @DeleteMapping("/api/v1/ticket-types/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ticketTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
