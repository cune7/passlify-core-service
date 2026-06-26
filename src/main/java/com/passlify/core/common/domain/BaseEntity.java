package com.passlify.core.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared identity for every aggregate root: an application-assigned UUID primary
 * key. Timestamps are declared per entity (the reference/lookup tables only have
 * {@code created_at}, the mutable tables have both), so they are intentionally
 * NOT on this superclass — keeping {@code ddl-auto=validate} happy.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
