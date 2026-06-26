package com.passlify.core.forms;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * An organizer-defined field collected at checkout (master spec §13). Identified
 * within an event by {@code fieldKey}; values are stored keyed by that key on the
 * order (PER_PURCHASE) or attendee (PER_ATTENDEE). {@code options} is a JSON array
 * of choices for SELECT fields (raw JSON string).
 */
@Getter
@Setter
@Entity
@Table(name = "custom_field")
public class CustomField extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldScope scope;

    @Column(nullable = false)
    private boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String options;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
