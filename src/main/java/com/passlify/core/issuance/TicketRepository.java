package com.passlify.core.issuance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByOrderId(UUID orderId);

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByOrderIdOrderBySerialNumberAsc(UUID orderId);

    Page<Ticket> findByOwnerCustomerId(String ownerCustomerId, Pageable pageable);

    Page<Ticket> findByOwnerCustomerIdAndEventId(String ownerCustomerId, UUID eventId, Pageable pageable);

    Page<Ticket> findByEventId(UUID eventId, Pageable pageable);

    List<Ticket> findByEventIdOrderBySerialNumberAsc(UUID eventId);

    /**
     * Locks the ticket row for the scan transaction (DOMAIN §4.7). The pessimistic
     * write lock serializes concurrent scans of the same ticket so only the first
     * VALID→USED transition wins — blocking double entry.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);

    /** Distinct holder emails (owner, else the buyer) of non-void tickets — for event notices. */
    @Query("select distinct coalesce(t.ownerEmail, t.order.customerEmail) from Ticket t "
            + "where t.event.id = :eventId and t.status <> com.passlify.core.issuance.TicketStatus.VOID")
    List<String> findHolderEmailsByEventId(@Param("eventId") UUID eventId);

    long countByEventId(UUID eventId);

    long countByEventIdAndStatus(UUID eventId, com.passlify.core.issuance.TicketStatus status);
}
