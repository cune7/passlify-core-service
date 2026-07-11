package com.passlify.core.event.dto;

import com.passlify.core.event.EventAuditAction;
import com.passlify.core.event.EventAuditEntry;
import java.time.Instant;
import java.util.UUID;

/** One entry of an event's audit history (reverse-chronological). */
public record EventAuditResponse(
        UUID id,
        UUID eventId,
        String actorUserId,
        EventAuditAction action,
        String changedFields,
        String reason,
        Instant occurredAt) {

    public static EventAuditResponse from(EventAuditEntry e) {
        return new EventAuditResponse(
                e.getId(), e.getEventId(), e.getActorUserId(), e.getAction(),
                e.getChangedFields(), e.getReason(), e.getOccurredAt());
    }
}
