package com.passlify.core.event.dto;

import com.passlify.core.event.EventAccessGrant;
import java.time.Instant;
import java.util.UUID;

/** A private-event access grant, including the shareable {@code token}. */
public record AccessGrantResponse(
        UUID id,
        UUID eventId,
        String token,
        String label,
        boolean revoked,
        Instant createdAt) {

    public static AccessGrantResponse from(EventAccessGrant g) {
        return new AccessGrantResponse(
                g.getId(), g.getEventId(), g.getToken(), g.getLabel(), g.isRevoked(), g.getCreatedAt());
    }
}
