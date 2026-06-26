package com.passlify.core.scan;

import com.passlify.core.common.domain.BaseEntity;
import com.passlify.core.event.Event;
import com.passlify.core.issuance.Ticket;
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

/**
 * One row per scan attempt — the fraud/entry audit log (replaces the legacy
 * UsageLog). {@code ticket}/{@code event} are nullable so we can still record a
 * scan when the token didn't resolve to a ticket.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_scan")
public class TicketScan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @CreationTimestamp
    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScanResult result;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ScanDenyReason reason;

    @Column(name = "scanned_by", length = 64)
    private String scannedBy;

    @Column(length = 80)
    private String gate;
}
