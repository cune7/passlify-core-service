package com.passlify.core.ticket;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventCapability;
import com.passlify.core.event.EventService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import com.passlify.core.ticket.dto.UpdateTicketTypeRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket-type management within an event. Ownership is enforced by routing every
 * load through {@link EventService#getOwned} (organizer-scoped, admin bypass).
 * Guards: cannot reduce {@code totalQuantity} below {@code soldQuantity}; cannot
 * delete a type that has sold tickets.
 */
@Service
public class
TicketTypeService {

    private final TicketTypeRepository ticketTypes;
    private final EventService eventService;
    private final TicketTypeValidator validator;

    public TicketTypeService(TicketTypeRepository ticketTypes,
                             EventService eventService,
                             TicketTypeValidator validator) {
        this.ticketTypes = ticketTypes;
        this.eventService = eventService;
        this.validator = validator;
    }

    @Transactional
    public TicketType create(UUID eventId, CreateTicketTypeRequest req) {
        Event event = eventService.getForCapability(eventId, EventCapability.CONFIGURE_TICKETS);
        validator.validateSalesWindow(req.salesStartAt(), req.salesEndAt());

        TicketType t = new TicketType();
        t.setEvent(event);
        t.setName(req.name());
        t.setDescription(req.description());
        t.setPriceMinor(req.priceMinor());
        t.setCurrency(req.currency() != null ? req.currency().toUpperCase(Locale.ROOT) : event.getCurrency());
        t.setTotalQuantity(req.totalQuantity());
        t.setSoldQuantity(0);
        t.setMaxPerOrder(req.maxPerOrder() != null ? req.maxPerOrder() : 10);
        t.setSalesStartAt(req.salesStartAt());
        t.setSalesEndAt(req.salesEndAt());
        t.setActive(req.active() == null || req.active());
        t.setVisibility(req.visibility() != null ? req.visibility() : TicketTypeVisibility.PUBLIC);
        t.setKind(req.kind() != null ? req.kind() : TicketKind.SINGLE_USE);
        t.setAttendeeDataMode(req.attendeeDataMode() != null ? req.attendeeDataMode() : AttendeeDataMode.BUYER_ONLY);
        t.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        return ticketTypes.save(t);
    }

    @Transactional(readOnly = true)
    public List<TicketType> listForEvent(UUID eventId) {
        eventService.getForCapability(eventId, EventCapability.VIEW);   // participation + existence
        return ticketTypes.findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId);
    }

    @Transactional
    public TicketType update(UUID ticketTypeId, UpdateTicketTypeRequest req) {
        TicketType t = loadOwned(ticketTypeId);

        if (req.totalQuantity() != null) {
            validator.assertCanSetTotalQuantity(t, req.totalQuantity());
            t.setTotalQuantity(req.totalQuantity());
        }

        applyUpdates(req, t);
        return t;
    }

    @Transactional
    public void delete(UUID ticketTypeId) {
        TicketType t = loadOwned(ticketTypeId);
        validator.assertDeletable(t);
        ticketTypes.delete(t);
    }

    // ---- helpers -----------------------------------------------------------

    private void applyUpdates(UpdateTicketTypeRequest req, TicketType t) {
        if (req.name() != null) {
            t.setName(req.name());
        }
        if (req.description() != null) {
            t.setDescription(req.description());
        }
        if (req.priceMinor() != null) {
            t.setPriceMinor(req.priceMinor());
        }
        if (req.currency() != null) {
            t.setCurrency(req.currency().toUpperCase(Locale.ROOT));
        }
        if (req.maxPerOrder() != null) {
            t.setMaxPerOrder(req.maxPerOrder());
        }
        if (req.active() != null) {
            t.setActive(req.active());
        }
        if (req.visibility() != null) {
            t.setVisibility(req.visibility());
        }
        if (req.kind() != null) {
            t.setKind(req.kind());
        }
        if (req.attendeeDataMode() != null) {
            t.setAttendeeDataMode(req.attendeeDataMode());
        }
        if (req.sortOrder() != null) {
            t.setSortOrder(req.sortOrder());
        }
        Instant newStart = req.salesStartAt() != null ? req.salesStartAt() : t.getSalesStartAt();
        Instant newEnd = req.salesEndAt() != null ? req.salesEndAt() : t.getSalesEndAt();
        validator.validateSalesWindow(newStart, newEnd);
        t.setSalesStartAt(newStart);
        t.setSalesEndAt(newEnd);
    }

    private TicketType loadOwned(UUID ticketTypeId) {
        TicketType t = ticketTypes.findById(ticketTypeId)
                .orElseThrow(() -> ApiException.notFound("Ticket type not found: " + ticketTypeId));
        eventService.getForCapability(t.getEvent().getId(), EventCapability.CONFIGURE_TICKETS);
        return t;
    }
}
