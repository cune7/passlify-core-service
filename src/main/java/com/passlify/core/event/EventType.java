package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Administrator-managed classification of an event (EVENT_DOMAIN_SPEC §19), modeled
 * as a hierarchy: non-selectable category rows (parent) with selectable leaf types
 * beneath them. Organizers may only attach a {@code selectable}, {@code active} leaf.
 *
 * <p>{@code parent} is EAGER because responses map {@code EventTypeDto} (including the
 * parent/category name) after the service transaction closes; the tree is only two
 * levels deep so the fetch is bounded.
 */
@Getter
@Setter
@Entity
@Table(name = "event_type")
public class EventType extends BaseEntity {

    /** Stable machine code, e.g. {@code MUSIC} or {@code MUSIC.FESTIVAL}. */
    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private EventType parent;

    /** True for leaf types an organizer can pick; false for category headings. */
    @Column(nullable = false)
    private boolean selectable = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
