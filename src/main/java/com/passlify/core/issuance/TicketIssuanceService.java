package com.passlify.core.issuance;

import com.passlify.core.event.Event;
import com.passlify.core.issuance.qr.QrTokenService;
import com.passlify.core.notification.TicketsIssuedEvent;
import com.passlify.core.order.Attendee;
import com.passlify.core.order.AttendeeRepository;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderItem;
import com.passlify.core.ticket.AttendeeDataMode;
import com.passlify.core.ticket.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues tickets on payment success (DOMAIN §4.5). One {@link Ticket} per quantity
 * unit, each with a §4.4 serial number and a signed QR token. Idempotent: if the
 * order already has tickets (e.g. a redelivered webhook that slipped past dedup),
 * it does nothing — no duplicates. Called from the payment/checkout flow, always
 * within the caller's transaction.
 */
@Service
public class TicketIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(TicketIssuanceService.class);

    private final TicketRepository tickets;
    private final AttendeeRepository attendees;
    private final QrTokenService qrTokenService;
    private final ApplicationEventPublisher events;

    public TicketIssuanceService(TicketRepository tickets, AttendeeRepository attendees,
                                 QrTokenService qrTokenService, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.attendees = attendees;
        this.qrTokenService = qrTokenService;
        this.events = events;
    }

    @Transactional
    public void issueForOrder(Order order) {
        if (tickets.existsByOrderId(order.getId())) {
            log.debug("Tickets already issued for order {} — skipping", order.getId());
            return;
        }
        if (order.getItems().isEmpty()) {
            return;
        }

        Event event = order.getItems().get(0).getTicketType().getEvent();
        String prefix = eventPrefix(event);
        String order6 = order.getId().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);

        int counter = 0;
        Instant now = Instant.now();
        for (OrderItem item : order.getItems()) {
            TicketType ticketType = item.getTicketType();
            // For EACH_TICKET types, attendees were captured at checkout (one per unit).
            List<Attendee> itemAttendees = ticketType.getAttendeeDataMode() == AttendeeDataMode.EACH_TICKET
                    ? attendees.findByOrderIdAndTicketTypeIdOrderByCreatedAtAsc(order.getId(), ticketType.getId())
                    : List.of();

            for (int i = 0; i < item.getQuantity(); i++) {
                counter++;
                UUID id = UUID.randomUUID();
                String serial = String.format("%s-%s-%03d", prefix, order6, counter);
                Attendee attendee = i < itemAttendees.size() ? itemAttendees.get(i) : null;

                Ticket ticket = new Ticket();
                ticket.setId(id);   // assign up front so the QR token can embed it
                ticket.setOrder(order);
                ticket.setOrderItem(item);
                ticket.setTicketType(ticketType);
                ticket.setEvent(event);
                ticket.setAttendee(attendee);
                ticket.setOwnerCustomerId(order.getCustomerId());
                ticket.setOwnerEmail(attendee != null && attendee.getEmail() != null
                        ? attendee.getEmail() : order.getCustomerEmail());
                ticket.setAttendeeName(attendeeName(attendee, order));
                ticket.setSerialNumber(serial);
                ticket.setQrToken(qrTokenService.sign(id, event.getId(), serial));
                ticket.setStatus(TicketStatus.VALID);
                ticket.setIssuedAt(now);
                tickets.save(ticket);
            }
        }
        log.info("Issued {} ticket(s) for order {}", counter, order.getId());
        // Delivered after commit, off-thread (see TicketIssuedListener).
        events.publishEvent(new TicketsIssuedEvent(order.getId()));
    }

    /**
     * Voids all tickets of an order (refund/cancellation). Already-VOID tickets are
     * left as-is. Returns how many were voided. {@code USED} tickets are also voided
     * — a refunded attendee who already entered still has the ticket invalidated.
     */
    @Transactional
    public int voidForOrder(UUID orderId) {
        int voided = 0;
        for (Ticket ticket : tickets.findByOrderId(orderId)) {
            if (ticket.getStatus() != TicketStatus.VOID) {
                ticket.setStatus(TicketStatus.VOID);
                voided++;
            }
        }
        if (voided > 0) {
            log.info("Voided {} ticket(s) for order {}", voided, orderId);
        }
        return voided;
    }

    private static String attendeeName(Attendee attendee, Order order) {
        if (attendee != null && attendee.getName() != null && !attendee.getName().isBlank()) {
            return attendee.getName();
        }
        return order.getCustomerName();
    }

    /** First 4 alphanumerics of the event slug (fallback to name), uppercased. */
    private String eventPrefix(Event event) {
        String source = event.getSlug() != null ? event.getSlug() : event.getName();
        String alnum = source == null ? "" : source.replaceAll("[^A-Za-z0-9]", "");
        if (alnum.isBlank()) {
            return "EVNT";
        }
        String prefix = alnum.substring(0, Math.min(4, alnum.length())).toUpperCase(Locale.ROOT);
        return prefix.length() == 4 ? prefix : (prefix + "EVNT").substring(0, 4);
    }
}
