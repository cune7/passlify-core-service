package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Seeded classification of an event (e.g. Music / Concert). Reference data. */
@Getter
@Setter
@Entity
@Table(name = "event_type")
public class EventType extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 120)
    private String type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
