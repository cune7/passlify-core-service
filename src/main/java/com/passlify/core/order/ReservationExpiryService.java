package com.passlify.core.order;

import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases inventory held by checkouts that never paid (DOMAIN §4.2, master spec
 * §22.3). Runs periodically: any {@code PENDING_PAYMENT} order past its
 * {@code expiresAt} has its lines' {@code soldQuantity} decremented and is moved
 * to {@code EXPIRED}. Idempotent — once EXPIRED an order is no longer picked up.
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final OrderRepository orders;
    private final TicketTypeRepository ticketTypes;

    public ReservationExpiryService(OrderRepository orders, TicketTypeRepository ticketTypes) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
    }

    @Scheduled(fixedDelayString = "${passlify.reservation.sweep-interval-ms:60000}")
    @Transactional
    public void expireStaleReservations() {
        List<Order> expired = orders.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (Order order : expired) {
            for (OrderItem item : order.getItems()) {
                ticketTypes.release(item.getTicketType().getId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.EXPIRED);
        }
        log.info("Expired {} stale reservation(s), released inventory", expired.size());
    }
}
