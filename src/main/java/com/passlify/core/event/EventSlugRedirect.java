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
 * Maps a retired event slug to its event so old links keep working after a slug change
 * (EVENT_DOMAIN_SPEC §5.3). Public detail-by-slug 301s an {@code oldSlug} to the event's
 * current slug.
 */
@Getter
@Setter
@Entity
@Table(name = "event_slug_redirect")
public class EventSlugRedirect extends BaseEntity {

    @Column(name = "old_slug", nullable = false, unique = true, length = 120)
    private String oldSlug;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
