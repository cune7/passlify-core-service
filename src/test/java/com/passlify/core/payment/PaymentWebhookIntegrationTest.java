package com.passlify.core.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.event.Event;
import com.passlify.core.event.EventRepository;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Visibility;
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

/** Slice-5 correctness: webhook idempotency and the failure→release path. */
class PaymentWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    WebhookEventRepository webhookEvents;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    PaymentService paymentService;

    @Test
    void paidWebhookMarksOrderPaidAndReplayIsIdempotent() {
        TicketType tt = persistOnSaleTicketType(50);
        Order order = checkoutService.createOrder(orderFor(tt, 2));
        PaymentSessionResponse session = paymentService.createSession(order.getId(), null, null);

        String body = "{\"type\":\"PAID\",\"sessionId\":\"" + session.sessionId() + "\"}";

        long ledgerBefore = webhookEvents.count();
        paymentService.handleWebhook("mock", body, null);
        paymentService.handleWebhook("mock", body, null);   // duplicate delivery

        Order paid = orders.findById(order.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();

        List<Payment> orderPayments = payments.findByOrderId(order.getId());
        assertThat(orderPayments).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED));

        // Exactly one ledger row → the replay was deduped, fulfillment fired once.
        assertThat(webhookEvents.count()).isEqualTo(ledgerBefore + 1);
        // Inventory stays reserved for a paid order.
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(2);
    }

    @Test
    void failedWebhookReleasesInventory() {
        TicketType tt = persistOnSaleTicketType(50);
        Order order = checkoutService.createOrder(orderFor(tt, 3));
        PaymentSessionResponse session = paymentService.createSession(order.getId(), null, null);
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(3);

        String body = "{\"type\":\"FAILED\",\"sessionId\":\"" + session.sessionId() + "\"}";
        paymentService.handleWebhook("mock", body, null);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isZero();
    }

    // ---- fixtures ----------------------------------------------------------

    private TicketType persistOnSaleTicketType(int capacity) {
        Event event = new Event();
        event.setName("Payment Fest");
        event.setSlug("payment-fest-" + UUID.randomUUID().toString().substring(0, 8));
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
        tt.setPriceMinor(200_000L);
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
