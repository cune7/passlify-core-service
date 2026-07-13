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

    private static final java.util.List<Visibility> LINKABLE =
            java.util.List.of(Visibility.PUBLIC, Visibility.UNLISTED);

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final EventSlugRedirectRepository slugRedirects;

    public PublicCatalogService(EventRepository events, TicketTypeRepository ticketTypes,
                                EventSlugRedirectRepository slugRedirects) {
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.slugRedirects = slugRedirects;
    }

    @Transactional(readOnly = true)
    public Page<Event> search(String q, String city, java.util.UUID eventTypeId,
                              Instant from, Instant to, Pageable pageable) {
        // Drop null fragments (optional filters that were not supplied); allOf rejects nulls.
        List<Specification<Event>> fragments = java.util.stream.Stream.of(
                        PublicEventSpecifications.publishedAndPublic(),
                        PublicEventSpecifications.nameContains(q),
                        PublicEventSpecifications.inCity(city),
                        PublicEventSpecifications.ofEventType(eventTypeId),
                        PublicEventSpecifications.startsFrom(from),
                        PublicEventSpecifications.startsUntil(to))
                .filter(java.util.Objects::nonNull)
                .toList();
        return events.findAll(Specification.allOf(fragments), pageable);
    }

    @Transactional(readOnly = true)
    public Event getPublishedBySlug(String slug) {
        return findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + slug));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Event> findPublishedBySlug(String slug) {
        return events.findBySlugAndStatusAndVisibilityIn(slug, EventStatus.PUBLISHED, LINKABLE);
    }

    /**
     * If {@code oldSlug} is a retired slug of a still-published event, the event's current
     * slug (for a 301). Empty if there's no redirect or the target is no longer public.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<String> findRedirectTargetSlug(String oldSlug) {
        return slugRedirects.findByOldSlug(oldSlug)
                .flatMap(r -> events.findById(r.getEventId()))
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED && LINKABLE.contains(e.getVisibility()))
                .map(Event::getSlug);
    }

    /** Publicly visible ticket types for an event (active + PUBLIC visibility). */
    @Transactional(readOnly = true)
    public List<TicketType> publicTicketTypes(java.util.UUID eventId) {
        return ticketTypes.findByEventIdAndActiveTrueAndVisibilityOrderBySortOrderAscCreatedAtAsc(
                eventId, TicketTypeVisibility.PUBLIC);
    }
}
