package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A shareable access grant for a PRIVATE event (EVENT_DOMAIN_SPEC §8): holding a valid,
 * non-revoked {@code token} lets someone view and buy an otherwise-hidden private event.
 * The organizer creates and revokes these.
 */
@Getter
@Setter
@Entity
@Table(name = "event_access_grant")
public class EventAccessGrant extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Opaque bearer token shared with the invitee (in the access link). */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** Optional note — e.g. the invitee's email or "VIP list". */
    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
