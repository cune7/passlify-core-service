package com.passlify.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventRepository;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Visibility;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Slice-4 correctness: no oversell under concurrency, server pricing, expiry release. */
class CheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Autowired
    OrderRepository orders;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    ReservationExpiryService reservationExpiryService;

    @Test
    void serverPricesTheOrderAndSnapshotsUnitPrice() {
        TicketType tt = persistOnSaleTicketType(100, 250_000L);

        Order order = checkoutService.createOrder(orderFor(tt, 3));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCurrency()).isEqualTo("RSD");
        assertThat(order.getTotalMinor()).isEqualTo(750_000L);
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getUnitPriceMinor()).isEqualTo(250_000L);
            assertThat(item.getTotalPriceMinor()).isEqualTo(750_000L);
        });
        assertThat(order.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void doesNotOversellUnderConcurrency() throws Exception {
        int capacity = 50;
        int attempts = 120;
        TicketType tt = persistOnSaleTicketType(capacity, 100_000L);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    checkoutService.createOrder(orderFor(tt, 1));
                    ok.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.getCode() == ErrorCode.SOLD_OUT) {
                        soldOut.incrementAndGet();
                    } else {
                        throw new RuntimeException(ex);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get()).isEqualTo(capacity);
        assertThat(soldOut.get()).isEqualTo(attempts - capacity);
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(capacity);
    }

    @Test
    void expiryReleasesInventoryAndMarksOrderExpired() {
        TicketType tt = persistOnSaleTicketType(10, 100_000L);
        Order order = checkoutService.createOrder(orderFor(tt, 4));
        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isEqualTo(4);

        // Force the hold to look stale, then run the sweep.
        order.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        orders.save(order);
        reservationExpiryService.expireStaleReservations();

        assertThat(ticketTypes.findById(tt.getId()).orElseThrow().getSoldQuantity()).isZero();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    // ---- fixtures ----------------------------------------------------------

    private TicketType persistOnSaleTicketType(int capacity, long priceMinor) {
        Event event = new Event();
        event.setName("Concurrency Fest");
        event.setSlug("concurrency-fest-" + java.util.UUID.randomUUID().toString().substring(0, 8));
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
