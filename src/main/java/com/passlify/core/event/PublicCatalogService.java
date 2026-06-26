package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import com.passlify.core.ticket.TicketTypeVisibility;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only buyer catalog: only PUBLISHED + PUBLIC events and their on-sale ticket types. */
@Service
public class PublicCatalogService {

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;

    public PublicCatalogService(EventRepository events, TicketTypeRepository ticketTypes) {
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    @Transactional(readOnly = true)
    public Page<Event> search(String q, String city, java.util.UUID eventTypeId,
                              Instant from, Instant to, Pageable pageable) {
        Specification<Event> spec = Specification.allOf(
                PublicEventSpecifications.publishedAndPublic(),
                PublicEventSpecifications.nameContains(q),
                PublicEventSpecifications.inCity(city),
                PublicEventSpecifications.ofEventType(eventTypeId),
                PublicEventSpecifications.startsFrom(from),
                PublicEventSpecifications.startsUntil(to));
        return events.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Event getPublishedBySlug(String slug) {
        return events.findBySlugAndStatusAndVisibility(slug, EventStatus.PUBLISHED, Visibility.PUBLIC)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + slug));
    }

    /** Publicly visible ticket types for an event (active + PUBLIC visibility). */
    @Transactional(readOnly = true)
    public List<TicketType> publicTicketTypes(java.util.UUID eventId) {
        return ticketTypes.findByEventIdAndActiveTrueAndVisibilityOrderBySortOrderAscCreatedAtAsc(
                eventId, TicketTypeVisibility.PUBLIC);
    }
}
