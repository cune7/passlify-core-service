package com.passlify.core.event.dto;

import com.passlify.core.event.Event;
import com.passlify.core.event.Location;
import java.time.Instant;
import java.util.UUID;

/** Compact event card for the public catalog listing. */
public record PublicEventSummary(
        UUID id,
        String slug,
        String name,
        String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        String city,
        EventTypeDto eventType) {

    public static PublicEventSummary from(Event e) {
        Location loc = e.getLocation();
        return new PublicEventSummary(
                e.getId(), e.getSlug(), e.getName(), e.getCoverImageUrl(),
                e.getStartsAt(), e.getEndsAt(),
                loc == null ? null : loc.getCity(),
                EventTypeDto.from(e.getEventType()));
    }
}
