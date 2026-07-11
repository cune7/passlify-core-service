package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable record of a material event change (EVENT_DOMAIN_SPEC §28). Written in the
 * same transaction as the change it describes; never updated or deleted through the
 * application. {@code changedFields} is a JSON diff ({@code {field:{from,to}}}).
 */
@Getter
@Setter
@Entity
@Table(name = "event_audit_entries")
public class EventAuditEntry extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_public_id", nullable = false, length = 26)
    private String eventPublicId;

    @Column(name = "actor_user_id", nullable = false, length = 64)
    private String actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private EventAuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", columnDefinition = "jsonb")
    private String changedFields;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
