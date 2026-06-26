package com.passlify.core.event.dto;

import com.passlify.core.event.Event;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Visibility;
import java.time.Instant;
import java.util.UUID;

/** Compact event view for list endpoints. */
public record EventSummary(
        UUID id,
        String slug,
        String name,
        EventStatus status,
        Visibility visibility,
        String currency,
        Instant startsAt,
        Instant endsAt) {

    public static EventSummary from(Event e) {
        return new EventSummary(
                e.getId(), e.getSlug(), e.getName(), e.getStatus(),
                e.getVisibility(), e.getCurrency(), e.getStartsAt(), e.getEndsAt());
    }
}
