package com.passlify.core.event.dto;

import com.passlify.core.event.AttendanceMode;
import com.passlify.core.event.CommercialMode;
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
        String publicId,
        String slug,
        String name,
        String descriptionHtml,
        String descriptionPlainText,
        String coverImageUrl,
        EventStatus status,
        Visibility visibility,
        AttendanceMode attendanceMode,
        CommercialMode commercialMode,
        String currency,
        PaymentProvider paymentProvider,
        Instant startsAt,
        Instant endsAt,
        String timezone,
        EventTypeDto eventType,
        LocationDto location,
        String organizerId,
        UUID organizationId,
        Integer capacity,
        Integer autoCompleteGraceHours,
        List<String> tags,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static EventResponse from(Event e) {
        Location loc = e.getLocation();
        LocationDto locationDto = loc == null ? null : new LocationDto(
                loc.getVenueName(), loc.getAddress(), loc.getCity(), loc.getCountry(), loc.getPostalCode());
        return new EventResponse(
                e.getId(),
                e.getPublicId(),
                e.getSlug(),
                e.getName(),
                e.getDescriptionHtml(),
                e.getDescriptionPlainText(),
                e.getCoverImageUrl(),
                e.getStatus(),
                e.getVisibility(),
                e.getAttendanceMode(),
                e.getCommercialMode(),
                e.getCurrency(),
                e.getPaymentProvider(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getTimezone(),
                EventTypeDto.from(e.getEventType()),
                locationDto,
                e.getOrganizerId(),
                e.getOrganizationId(),
                e.getCapacity(),
                e.getAutoCompleteGraceHours(),
                e.getTags(),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
