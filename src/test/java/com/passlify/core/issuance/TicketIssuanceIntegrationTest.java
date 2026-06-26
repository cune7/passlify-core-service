package com.passlify.core.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.event.Event;
import com.passlify.core.event.EventRepository;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Visibility;
import com.passlify.core.issuance.qr.QrTokenService;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.payment.PaymentService;
import com.passlify.core.payment.dto.PaymentSessionResponse;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Slice-6 correctness: issuance on PAID is idempotent + signed QR; free orders issue at once. */
class TicketIssuanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    TicketRepository tickets;

    @Autowired
    QrTokenService qrTokenService;

    @Test
    void paidOrderIssuesSignedTicketsIdempotently() {
        TicketType tt = persistTicketType(50, 200_000L);
        Order order = checkoutService.createOrder(orderFor(tt, 2));
        PaymentSessionResponse session = paymentService.createSession(order.getId(), null, null);

        String body = "{\"type\":\"PAID\",\"sessionId\":\"" + session.sessionId() + "\"}";
        paymentService.handleWebhook("mock", body, null);
        paymentService.handleWebhook("mock", body, null);   // redelivery → no duplicate tickets

        List<Ticket> issued = tickets.findByOrderIdOrderBySerialNumberAsc(order.getId());
        assertThat(issued).hasSize(2);
        assertThat(issued).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(TicketStatus.VALID);
            assertThat(t.getSerialNumber()).matches("[A-Z0-9]{4}-[A-F0-9]{6}-\\d{3}");
            // QR token verifies and resolves back to this exact ticket + event.
            QrTokenService.VerifiedToken v = qrTokenService.verify(t.getQrToken());
            assertThat(v.ticketId()).isEqualTo(t.getId());
            assertThat(v.eventId()).isEqualTo(t.getEvent().getId());
        });
    }

    @Test
    void freeOrderIsPaidAndIssuedImmediately() {
        TicketType tt = persistTicketType(50, 0L);
        Order order = checkoutService.createOrder(orderFor(tt, 3));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(tickets.findByOrderIdOrderBySerialNumberAsc(order.getId())).hasSize(3);
    }

    // ---- fixtures ----------------------------------------------------------

    private TicketType persistTicketType(int capacity, long priceMinor) {
        Event event = new Event();
        event.setName("Issuance Fest");
        event.setSlug("issuance-fest-" + UUID.randomUUID().toString().substring(0, 8));
        event.setCurrency("RSD");
        event.setOrganizerId("organizer-1");
        event.setStatus(EventStatus.PUBLISHED);
        event.setVisibility(Visibility.PUBLIC);
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        event.setStartsAt(start);
        event.setEndsAt(start.plus(4, ChronoUnit.HOURS));
        Event savedEvent = events.save(event);

        TicketType tt = new TicketType();
        tt.setEvent(savedEvent);
        tt.setName("General");
        tt.setCurrency("RSD");
        tt.setPriceMinor(priceMinor);
        tt.setTotalQuantity(capacity);
        tt.setMaxPerOrder(10);
        return ticketTypes.save(tt);
    }

    private CreateOrderRequest orderFor(TicketType tt, int quantity) {
        return new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Buyer", null),
                List.of(new CreateOrderRequest.Line(tt.getId(), quantity, null)),
                null);
    }
}
