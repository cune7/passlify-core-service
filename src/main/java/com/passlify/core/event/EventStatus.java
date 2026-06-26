package com.passlify.core.event;

/**
 * Event lifecycle: {@code DRAFT → PUBLISHED → COMPLETED}, or {@code CANCELLED}
 * from any non-completed state.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    COMPLETED,
    CANCELLED
}
