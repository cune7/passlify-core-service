package com.passlify.core.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Idempotency ledger: every received provider webhook is recorded before being
 * processed (DOMAIN §4.3). A duplicate (provider, id) means "already seen" — the
 * handler ACKs and skips. The raw payload is kept for audit/replay.
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_event")
@IdClass(WebhookEventKey.class)
public class WebhookEvent {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private PaymentProvider provider;

    @Column(nullable = false, length = 120)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
