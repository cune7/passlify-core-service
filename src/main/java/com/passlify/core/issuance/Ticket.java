package com.passlify.core.issuance;

import com.passlify.core.common.domain.BaseEntity;
import com.passlify.core.event.Event;
import com.passlify.core.order.Attendee;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderItem;
import com.passlify.core.ticket.TicketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An issued, scannable ticket — the unit of access. Created on payment success
 * (one per quantity unit), carries a signed {@code qrToken}, and moves
 * {@code VALID → USED} on scan. {@code event}/{@code ticketType} are EAGER so the
 * response/PDF can be rendered after the service transaction closes.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket")
public class Ticket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** The attendee this ticket was issued to (EACH_TICKET mode); null for BUYER_ONLY. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id")
    private Attendee attendee;

    @Column(name = "owner_customer_id", length = 64)
    private String ownerCustomerId;

    @Column(name = "owner_email", length = 320)
    private String ownerEmail;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Column(name = "qr_token", nullable = false, length = 512)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketStatus status = TicketStatus.VALID;

    @Column(name = "attendee_name", length = 256)
    private String attendeeName;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "scan_count", nullable = false)
    private int scanCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
