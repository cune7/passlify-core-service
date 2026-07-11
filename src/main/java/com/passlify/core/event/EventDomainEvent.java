package com.passlify.core.event;

import java.util.UUID;

/**
 * Internal domain events published by the Event module (EVENT_DOMAIN_SPEC §29) via
 * Spring's ApplicationEventPublisher and consumed with {@code @TransactionalEventListener}.
 * Cross-module side effects (notification, search, order/ticket reactions) subscribe
 * to these rather than being invoked inside the event transaction.
 */
public final class EventDomainEvent {

    private EventDomainEvent() {
    }

    public record Created(UUID eventId, String publicId, String organizerId) {
    }

    public record Published(UUID eventId, String publicId) {
    }

    public record Cancelled(UUID eventId, String publicId, String reason) {
    }

    public record Completed(UUID eventId, String publicId) {
    }
}
