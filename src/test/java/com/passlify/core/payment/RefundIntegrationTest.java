package com.passlify.core.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.event.Event;
import com.passlify.core.event.EventRepository;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Visibility;
import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.issuance.TicketStatus;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderRepository;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.order.dto.CreateOrderRequest;
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

/** Slice-8 correctness: full refund voids tickets + releases inventory; partial does not. */
class RefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    TicketRepository tickets;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    PaymentService paymentService;

    @Test
    void fullRefundVoidsTicketsAndReleasesInventory() {
        TicketType tt = persistTicketType(50, 200_000L);
        PaidOrder paid = payOrder(tt, 2);
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(2);

        String refund = "{\"type\":\"REFUNDED\",\"sessionId\":\"" + paid.sessionId + "\"}";
        paymentService.handleWebhook("mock", refund, null);
        paymentService.handleWebhook("mock", refund, null);   // replay → idempotent

        assertThat(orders.findById(paid.orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        Payment payment = payments.findByOrderId(paid.orderId).get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedMinor()).isEqualTo(payment.getAmountMinor());

        List<Ticket> ticketsForOrder = tickets.findByOrderIdOrderBySerialNumberAsc(paid.orderId);
        assertThat(ticketsForOrder).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.VOID));
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isZero();
    }

    @Test
    void partialRefundKeepsTicketsAndInventory() {
        TicketType tt = persistTicketType(50, 200_000L);
        PaidOrder paid = payOrder(tt, 2);   // total 400_000

        String refund = "{\"type\":\"REFUNDED\",\"sessionId\":\"" + paid.sessionId + "\",\"refundedMinor\":100000}";
        paymentService.handleWebhook("mock", refund, null);

        assertThat(orders.findById(paid.orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
        Payment payment = payments.findByOrderId(paid.orderId).get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedMinor()).isEqualTo(100_000L);

        List<Ticket> ticketsForOrder = tickets.findByOrderIdOrderBySerialNumberAsc(paid.orderId);
        assertThat(ticketsForOrder).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.VALID));
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(2);
    }

    // ---- fixtures ----------------------------------------------------------

    private record PaidOrder(UUID orderId, String sessionId) {
    }

    private PaidOrder payOrder(TicketType tt, int quantity) {
        Order order = checkoutService.createOrder(orderFor(tt, quantity));
        PaymentSessionResponse session = paymentService.createSession(order.getId(), null, null);
        paymentService.handleWebhook("mock",
                "{\"type\":\"PAID\",\"sessionId\":\"" + session.sessionId() + "\"}", null);
        return new PaidOrder(order.getId(), session.sessionId());
    }

    private TicketType persistTicketType(int capacity, long priceMinor) {
        Event event = new Event();
        event.setName("Refund Fest");
        event.setSlug("refund-fest-" + UUID.randomUUID().toString().substring(0, 8));
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
