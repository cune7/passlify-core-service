package com.passlify.core.scan;

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
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.scan.dto.ScanResponse;
import com.passlify.core.scan.dto.ScanSummaryResponse;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Slice-7 correctness: a ticket scanned concurrently is admitted exactly once. */
class ScanIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    TicketRepository tickets;

    @Autowired
    ScanService scanService;

    @Test
    void concurrentScansAdmitTicketExactlyOnce() throws Exception {
        Ticket ticket = issueOneFreeTicket();
        String token = ticket.getQrToken();
        UUID eventId = ticket.getEvent().getId();

        int scanners = 16;
        ExecutorService pool = Executors.newFixedThreadPool(scanners);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();

        for (int i = 0; i < scanners; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    ScanResponse r = scanService.scan(token, eventId, "gate-" + n, "operator-" + n);
                    if (r.result() == ScanResult.ALLOWED) {
                        allowed.incrementAndGet();
                    } else if (r.reason() == ScanDenyReason.ALREADY_USED) {
                        alreadyUsed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(1);
        assertThat(alreadyUsed.get()).isEqualTo(scanners - 1);

        Ticket reloaded = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(reloaded.getScanCount()).isEqualTo(1);
    }

    @Test
    void badSignatureIsDeniedNotErrored() {
        Ticket ticket = issueOneFreeTicket();
        ScanResponse r = scanService.scan("totally-bogus-token", ticket.getEvent().getId(), null, "op");
        assertThat(r.result()).isEqualTo(ScanResult.DENIED);
        assertThat(r.reason()).isEqualTo(ScanDenyReason.BAD_SIGNATURE);
        assertThat(r.ticket()).isNull();
    }

    @Test
    void summaryReflectsScans() {
        // 3 free tickets in one order, scan one in.
        TicketType tt = persistTicketType(50, 0L);
        Order order = checkoutService.createOrder(orderFor(tt, 3));
        List<Ticket> issued = tickets.findByOrderIdOrderBySerialNumberAsc(order.getId());
        UUID eventId = issued.get(0).getEvent().getId();

        scanService.scan(issued.get(0).getQrToken(), eventId, null, "op");

        ScanSummaryResponse summary = scanService.summary(eventId);
        assertThat(summary.issued()).isEqualTo(3);
        assertThat(summary.used()).isEqualTo(1);
        assertThat(summary.valid()).isEqualTo(2);
        assertThat(summary.voided()).isZero();
    }

    // ---- fixtures ----------------------------------------------------------

    private Ticket issueOneFreeTicket() {
        TicketType tt = persistTicketType(50, 0L);
        Order order = checkoutService.createOrder(orderFor(tt, 1));
        return tickets.findByOrderIdOrderBySerialNumberAsc(order.getId()).get(0);
    }

    private TicketType persistTicketType(int capacity, long priceMinor) {
        Event event = new Event();
        event.setName("Scan Fest");
        event.setSlug("scan-fest-" + UUID.randomUUID().toString().substring(0, 8));
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
