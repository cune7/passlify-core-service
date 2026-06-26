package com.passlify.core.event.dto;

import com.passlify.core.event.Event;
import com.passlify.core.event.Location;
import com.passlify.core.forms.dto.CustomFieldResponse;
import com.passlify.core.ticket.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full public event page: event details plus the ticket types on sale. */
public record PublicEventDetail(
        UUID id,
        String slug,
        String name,
        String description,
        String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        String currency,
        EventTypeDto eventType,
        LocationDto location,
        List<PublicTicketTypeDto> ticketTypes,
        List<CustomFieldResponse> customFields) {

    public static PublicEventDetail from(Event e, List<TicketType> ticketTypes,
                                         List<CustomFieldResponse> customFields) {
        Location loc = e.getLocation();
        LocationDto locationDto = loc == null ? null : new LocationDto(
                loc.getVenueName(), loc.getAddress(), loc.getCity(), loc.getCountry(), loc.getPostalCode());
        return new PublicEventDetail(
                e.getId(), e.getSlug(), e.getName(), e.getDescription(), e.getCoverImageUrl(),
                e.getStartsAt(), e.getEndsAt(), e.getCurrency(),
                EventTypeDto.from(e.getEventType()), locationDto,
                ticketTypes.stream().map(PublicTicketTypeDto::from).toList(),
                customFields);
    }
}
