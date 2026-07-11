package com.passlify.core.ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderBySortOrderAscCreatedAtAsc(UUID eventId);

    List<TicketType> findByEventIdAndActiveTrueAndVisibilityOrderBySortOrderAscCreatedAtAsc(
            UUID eventId, TicketTypeVisibility visibility);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    long countByEventIdAndActiveTrue(UUID eventId);

    /** True if the event has any active, priced ticket type (i.e. it is a paid event). */
    boolean existsByEventIdAndActiveTrueAndPriceMinorGreaterThan(UUID eventId, long priceMinor);

    /** Sum of sellable quantity across active ticket types (for the event-capacity ceiling). */
    @Query("select coalesce(sum(t.totalQuantity), 0) from TicketType t "
            + "where t.event.id = :eventId and t.active = true")
    long sumActiveTotalQuantity(@Param("eventId") UUID eventId);

    /**
     * Atomic inventory reservation (no oversell). Increments {@code soldQuantity}
     * only if capacity remains. Returns the number of rows updated: 1 on success,
     * 0 when sold out — the caller fails the whole order on 0. This conditional
     * UPDATE is the primary oversell guard (DOMAIN §4.2); no explicit row lock needed.
     */
    @Modifying
    @Query("""
            update TicketType t
               set t.soldQuantity = t.soldQuantity + :qty
             where t.id = :id
               and t.soldQuantity + :qty <= t.totalQuantity
            """)
    int reserve(@Param("id") UUID id, @Param("qty") int qty);

    /** Releases reserved/sold inventory (order expired/failed/cancelled/refunded). */
    @Modifying
    @Query("""
            update TicketType t
               set t.soldQuantity = t.soldQuantity - :qty
             where t.id = :id
               and t.soldQuantity >= :qty
            """)
    int release(@Param("id") UUID id, @Param("qty") int qty);
}
