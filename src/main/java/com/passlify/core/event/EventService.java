package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.LocationDto;
import com.passlify.core.event.dto.UpdateEventRequest;
import com.passlify.core.organization.Organization;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.payment.PaymentProvider;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event lifecycle and ownership rules (DOMAIN §2.3). Business logic lives here,
 * not in the controller. Ownership: an organizer may only touch their own events;
 * an ADMIN bypasses the ownership check.
 */
@Service
public class EventService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventRepository events;
    private final EventTypeRepository eventTypes;
    private final LocationRepository locations;
    private final OrganizationService organizations;
    private final EventValidator validator;
    private final CurrentUser currentUser;
    private final String defaultCurrency;

    public EventService(EventRepository events,
                        EventTypeRepository eventTypes,
                        LocationRepository locations,
                        OrganizationService organizations,
                        EventValidator validator,
                        CurrentUser currentUser,
                        @Value("${passlify.default-currency:RSD}") String defaultCurrency) {
        this.events = events;
        this.eventTypes = eventTypes;
        this.locations = locations;
        this.organizations = organizations;
        this.validator = validator;
        this.currentUser = currentUser;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public Event create(CreateEventRequest req) {
        validator.validateDates(req.startsAt(), req.endsAt());
        Event e = new Event();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCoverImageUrl(req.coverImageUrl());
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        e.setCapacity(req.capacity());
        e.setTags(req.tags());
        e.setStatus(EventStatus.DRAFT);
        e.setVisibility(req.visibility() != null ? req.visibility() : Visibility.PRIVATE);
        e.setCurrency(normalizeCurrency(req.currency()));
        e.setPaymentProvider(req.paymentProvider() != null ? req.paymentProvider() : PaymentProvider.MOCK);
        e.setEventType(resolveEventType(req.eventTypeId()));
        e.setLocation(resolveLocation(req.locationId(), req.location()));
        e.setOrganizerId(currentUser.requireSubject());
        // Every event belongs to the organizer's organization; create a minimal
        // INDIVIDUAL profile on first use (enough for free events).
        Organization org = organizations.getOrCreateForCurrentUser();
        e.setOrganizationId(org.getId());
        e.setSlug(generateUniqueSlug(req.name()));
        return events.save(e);
    }

    @Transactional(readOnly = true)
    public Event getOwned(UUID id) {
        return loadOwned(id);
    }

    @Transactional(readOnly = true)
    public Page<Event> listOwned(EventStatus status, Pageable pageable) {
        String organizer = currentUser.requireSubject();
        return status == null
                ? events.findByOrganizerId(organizer, pageable)
                : events.findByOrganizerIdAndStatus(organizer, status, pageable);
    }

    @Transactional
    public Event update(UUID id, UpdateEventRequest req) {
        Event e = loadOwned(id);

        Instant newStart = req.startsAt() != null ? req.startsAt() : e.getStartsAt();
        Instant newEnd = req.endsAt() != null ? req.endsAt() : e.getEndsAt();
        validator.validateDates(newStart, newEnd);
        e.setStartsAt(newStart);
        e.setEndsAt(newEnd);

        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.coverImageUrl() != null) {
            e.setCoverImageUrl(req.coverImageUrl());
        }
        if (req.capacity() != null) {
            e.setCapacity(req.capacity());
        }
        if (req.tags() != null) {
            e.setTags(req.tags());
        }
        if (req.visibility() != null) {
            e.setVisibility(req.visibility());
        }
        if (req.currency() != null) {
            e.setCurrency(normalizeCurrency(req.currency()));
        }
        if (req.paymentProvider() != null) {
            e.setPaymentProvider(req.paymentProvider());
        }
        if (req.eventTypeId() != null) {
            e.setEventType(resolveEventType(req.eventTypeId()));
        }
        if (req.locationId() != null || req.location() != null) {
            e.setLocation(resolveLocation(req.locationId(), req.location()));
        }
        // slug is immutable once published; name changes never rewrite it here.
        return e;
    }

    @Transactional
    public Event publish(UUID id) {
        Event e = loadOwned(id);
        validator.assertPublishable(e);
        e.setStatus(EventStatus.PUBLISHED);
        return e;
    }

    @Transactional
    public Event unpublish(UUID id) {
        Event e = loadOwned(id);
        validator.assertUnpublishable(e);
        e.setStatus(EventStatus.DRAFT);
        return e;
    }

    @Transactional
    public Event cancel(UUID id) {
        Event e = loadOwned(id);
        validator.assertCancellable(e);
        e.setStatus(EventStatus.CANCELLED);
        return e;
    }

    // ---- helpers -----------------------------------------------------------

    /** Loads an event the caller is allowed to manage, else 404 (no existence leak). */
    private Event loadOwned(UUID id) {
        if (currentUser.isAdmin()) {
            return events.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Event not found: " + id));
        }
        String organizer = currentUser.requireSubject();
        return events.findByIdAndOrganizerId(id, organizer)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + id));
    }

    private String normalizeCurrency(String currency) {
        String c = currency != null ? currency : defaultCurrency;
        return c.toUpperCase(Locale.ROOT);
    }

    private EventType resolveEventType(UUID eventTypeId) {
        if (eventTypeId == null) {
            return null;
        }
        return eventTypes.findById(eventTypeId)
                .orElseThrow(() -> ApiException.notFound("Event type not found: " + eventTypeId));
    }

    private Location resolveLocation(UUID locationId, LocationDto dto) {
        if (locationId != null) {
            return locations.findById(locationId)
                    .orElseThrow(() -> ApiException.notFound("Location not found: " + locationId));
        }
        if (dto == null) {
            return null;
        }
        return locations
                .findByVenueNameAndAddressAndCityAndCountryAndPostalCode(
                        dto.venueName(), dto.address(), dto.city(),
                        dto.country().toUpperCase(Locale.ROOT), dto.postalCode())
                .orElseGet(() -> {
                    Location loc = new Location();
                    loc.setVenueName(dto.venueName());
                    loc.setAddress(dto.address());
                    loc.setCity(dto.city());
                    loc.setCountry(dto.country().toUpperCase(Locale.ROOT));
                    loc.setPostalCode(dto.postalCode());
                    return locations.save(loc);
                });
    }

    private String generateUniqueSlug(String name) {
        String base = slugify(name);
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = base + "-" + randomSuffix();
            if (!events.existsBySlug(candidate)) {
                return candidate;
            }
        }
        // Extremely unlikely; fall back to a full UUID suffix.
        return base + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static String slugify(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isBlank()) {
            slug = "event";
        }
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private static String randomSuffix() {
        return Integer.toHexString(RANDOM.nextInt(0x10000) + 0x10000).substring(1);
    }
}
