package com.passlify.core.dashboard;

import com.passlify.core.dashboard.dto.AttendeeRow;
import com.passlify.core.dashboard.dto.OrderSummary;
import com.passlify.core.dashboard.dto.SalesSummaryResponse;
import com.passlify.core.dashboard.dto.SalesSummaryResponse.TicketTypeSales;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventCapability;
import com.passlify.core.event.EventService;
import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.issuance.TicketStatus;
import com.passlify.core.order.OrderRepository;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for the organizer dashboard. Every method routes through
 * {@link EventService#getOwned} first, so an organizer only ever sees their own
 * event's data (ADMIN bypasses). DTOs are built inside the transaction
 * (open-in-view=false).
 */
@Service
public class DashboardService {

    private final EventService eventService;
    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final TicketTypeRepository ticketTypes;

    public DashboardService(EventService eventService, OrderRepository orders,
                            TicketRepository tickets, TicketTypeRepository ticketTypes) {
        this.eventService = eventService;
        this.orders = orders;
        this.tickets = tickets;
        this.ticketTypes = ticketTypes;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> listOrders(UUID eventId, Pageable pageable) {
        eventService.getForCapability(eventId, EventCapability.VIEW_REPORTS);   // ownership + existence
        return orders.findByEventId(eventId, pageable).map(OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AttendeeRow> listAttendees(UUID eventId, Pageable pageable) {
        eventService.getForCapability(eventId, EventCapability.VIEW_REPORTS);
        return tickets.findByEventId(eventId, pageable).map(AttendeeRow::from);
    }

    @Transactional(readOnly = true)
    public SalesSummaryResponse salesSummary(UUID eventId) {
        Event event = eventService.getForCapability(eventId, EventCapability.VIEW_REPORTS);
        List<TicketTypeSales> byType = ticketTypes
                .findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId).stream()
                .map(TicketTypeSales::from)
                .toList();
        return new SalesSummaryResponse(
                event.getCurrency(),
                tickets.countByEventId(eventId),
                tickets.countByEventIdAndStatus(eventId, TicketStatus.USED),
                orders.countByEventIdAndStatus(eventId, OrderStatus.PAID),
                orders.sumPaidRevenueMinor(eventId),
                byType);
    }

    /** Attendee list as CSV (RFC-4180 quoting). */
    @Transactional(readOnly = true)
    public String attendeesCsv(UUID eventId) {
        eventService.getForCapability(eventId, EventCapability.VIEW_REPORTS);
        StringBuilder sb = new StringBuilder();
        sb.append("serial_number,ticket_type,attendee_name,email,status,checked_in_at,order_id\n");
        for (Ticket t : tickets.findByEventIdOrderBySerialNumberAsc(eventId)) {
            row(sb,
                    t.getSerialNumber(),
                    t.getTicketType().getName(),
                    t.getAttendeeName(),
                    t.getOwnerEmail(),
                    t.getStatus().name(),
                    t.getUsedAt() == null ? "" : t.getUsedAt().toString(),
                    t.getOrder().getId().toString());
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csv(fields[i]));
        }
        sb.append('\n');
    }

    private static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
