package com.passlify.core.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Stale unpaid orders whose reservation hold has lapsed — to be expired/released. */
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff);

    /** Orders containing at least one item for the event (orders link to events via items). */
    @Query(value = "select distinct o from Order o join o.items i where i.ticketType.event.id = :eventId",
            countQuery = "select count(distinct o) from Order o join o.items i where i.ticketType.event.id = :eventId")
    Page<Order> findByEventId(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("""
            select count(o) from Order o
             where o.status = :status
               and o.id in (select i.order.id from OrderItem i where i.ticketType.event.id = :eventId)
            """)
    long countByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") OrderStatus status);

    /** Gross revenue from PAID orders for the event. Uses a subquery so a multi-line order isn't double-counted. */
    @Query("""
            select coalesce(sum(o.totalMinor), 0) from Order o
             where o.status = com.passlify.core.order.OrderStatus.PAID
               and o.id in (select i.order.id from OrderItem i where i.ticketType.event.id = :eventId)
            """)
    long sumPaidRevenueMinor(@Param("eventId") UUID eventId);
}
