package com.passlify.core.event.dto;

import com.passlify.core.event.Event;
import com.passlify.core.event.EventStatus;
import com.passlify.core.event.Location;
import com.passlify.core.event.Visibility;
import com.passlify.core.payment.PaymentProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full organizer-facing event view. */
public record EventResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String coverImageUrl,
        EventStatus status,
        Visibility visibility,
        String currency,
        PaymentProvider paymentProvider,
        Instant startsAt,
        Instant endsAt,
        EventTypeDto eventType,
        LocationDto location,
        String organizerId,
        Integer capacity,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static EventResponse from(Event e) {
        Location loc = e.getLocation();
        LocationDto locationDto = loc == null ? null : new LocationDto(
                loc.getVenueName(), loc.getAddress(), loc.getCity(), loc.getCountry(), loc.getPostalCode());
        return new EventResponse(
                e.getId(),
                e.getSlug(),
                e.getName(),
                e.getDescription(),
                e.getCoverImageUrl(),
                e.getStatus(),
                e.getVisibility(),
                e.getCurrency(),
                e.getPaymentProvider(),
                e.getStartsAt(),
                e.getEndsAt(),
                EventTypeDto.from(e.getEventType()),
                locationDto,
                e.getOrganizerId(),
                e.getCapacity(),
                e.getTags(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
