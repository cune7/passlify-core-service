package com.passlify.core.ticket;

import com.passlify.core.common.domain.BaseEntity;
import com.passlify.core.event.Event;
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
 * A sellable category of ticket within an event ("VIP", "Regular", "Student").
 * Price is authoritative — the server computes order totals, never the client.
 * {@code soldQuantity} is a denormalized counter guarded by an atomic conditional
 * UPDATE during checkout (no oversell).
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_type")
public class TicketType extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder = 10;

    @Column(name = "sales_start_at")
    private Instant salesStartAt;

    @Column(name = "sales_end_at")
    private Instant salesEndAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketTypeVisibility visibility = TicketTypeVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketKind kind = TicketKind.SINGLE_USE;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_data_mode", nullable = false, length = 20)
    private AttendeeDataMode attendeeDataMode = AttendeeDataMode.BUYER_ONLY;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public int availableQuantity() {
        return totalQuantity - soldQuantity;
    }
}
