package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.common.text.HtmlSanitizationService;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.LocationDto;
import com.passlify.core.event.dto.UpdateEventRequest;
import com.passlify.core.event.dto.EventAuditResponse;
import com.passlify.core.event.dto.PublicationReadinessResponse;
import com.passlify.core.organization.Organization;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.payment.PaymentProvider;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PublicIdGenerator publicIds;
    private final HtmlSanitizationService htmlSanitizer;
    private final EventAuditService audit;
    private final EventPublicationReadinessValidator readiness;
    private final ApplicationEventPublisher domainEvents;
    private final String defaultCurrency;

    public EventService(EventRepository events,
                        EventTypeRepository eventTypes,
                        LocationRepository locations,
                        OrganizationService organizations,
                        EventValidator validator,
                        CurrentUser currentUser,
                        PublicIdGenerator publicIds,
                        HtmlSanitizationService htmlSanitizer,
                        EventAuditService audit,
                        EventPublicationReadinessValidator readiness,
                        ApplicationEventPublisher domainEvents,
                        @Value("${passlify.default-currency:RSD}") String defaultCurrency) {
        this.events = events;
        this.eventTypes = eventTypes;
        this.locations = locations;
        this.organizations = organizations;
        this.validator = validator;
        this.currentUser = currentUser;
        this.publicIds = publicIds;
        this.htmlSanitizer = htmlSanitizer;
        this.audit = audit;
        this.readiness = readiness;
        this.domainEvents = domainEvents;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public Event create(CreateEventRequest req) {
        validator.validateDates(req.startsAt(), req.endsAt());
        String subject = currentUser.requireSubject();
        Event e = new Event();
        e.setName(req.name());
        String sanitized = htmlSanitizer.sanitizeHtml(req.descriptionHtml());
        e.setDescriptionHtml(sanitized);
        e.setDescriptionPlainText(htmlSanitizer.toPlainText(sanitized));
        e.setCoverImageUrl(req.coverImageUrl());
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        e.setTimezone(normalizeTimezone(req.timezone()));
        e.setCapacity(req.capacity());
        e.setTags(req.tags());
        e.setStatus(EventStatus.DRAFT);
        e.setVisibility(req.visibility() != null ? req.visibility() : Visibility.PRIVATE);
        e.setAttendanceMode(req.attendanceMode() != null ? req.attendanceMode() : AttendanceMode.IN_PERSON);
        CommercialMode commercialMode = req.commercialMode() != null ? req.commercialMode() : CommercialMode.FREE;
        e.setCommercialMode(commercialMode);
        e.setCurrency(normalizeCurrency(req.currency()));
        e.setPaymentProvider(resolveProvider(commercialMode, req.paymentProvider()));
        e.setEventType(resolveEventType(req.eventTypeId()));
        e.setLocation(resolveLocation(req.locationId(), req.location()));
        e.setOrganizerId(subject);
        // Every event belongs to the organizer's organization; create a minimal
        // INDIVIDUAL profile on first use (enough for free events).
        Organization org = organizations.getOrCreateForCurrentUser();
        e.setOrganizationId(org.getId());
        e.setPublicId(publicIds.newId());
        e.setSlug(generateUniqueSlug(req.name()));
        e.setCreatedBy(subject);
        e.setUpdatedBy(subject);
        Event saved = events.save(e);
        audit.record(saved, EventAuditAction.EVENT_CREATED, null, null);
        domainEvents.publishEvent(
                new EventDomainEvent.Created(saved.getId(), saved.getPublicId(), saved.getOrganizerId()));
        return saved;
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
        // Optimistic concurrency: reject a stale edit up front with a clean 409
        // rather than relying only on the flush-time OptimisticLockException.
        if (req.version() != null && req.version() != e.getVersion()) {
            throw ApiException.of(ErrorCode.CONFLICT,
                    "Event was modified by someone else; reload and retry");
        }
        Map<String, Object> before = snapshot(e);

        Instant newStart = req.startsAt() != null ? req.startsAt() : e.getStartsAt();
        Instant newEnd = req.endsAt() != null ? req.endsAt() : e.getEndsAt();
        validator.validateDates(newStart, newEnd);
        e.setStartsAt(newStart);
        e.setEndsAt(newEnd);

        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.slug() != null) {
            applySlugChange(e, req.slug());
        }
        if (req.descriptionHtml() != null) {
            String sanitized = htmlSanitizer.sanitizeHtml(req.descriptionHtml());
            e.setDescriptionHtml(sanitized);
            e.setDescriptionPlainText(htmlSanitizer.toPlainText(sanitized));
        }
        if (req.coverImageUrl() != null) {
            e.setCoverImageUrl(req.coverImageUrl());
        }
        if (req.timezone() != null) {
            e.setTimezone(normalizeTimezone(req.timezone()));
        }
        if (req.attendanceMode() != null) {
            e.setAttendanceMode(req.attendanceMode());
        }
        if (req.commercialMode() != null) {
            e.setCommercialMode(req.commercialMode());
            e.setPaymentProvider(resolveProvider(req.commercialMode(), req.paymentProvider()));
        } else if (req.paymentProvider() != null) {
            e.setPaymentProvider(resolveProvider(e.getCommercialMode(), req.paymentProvider()));
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
        if (req.eventTypeId() != null) {
            e.setEventType(resolveEventType(req.eventTypeId()));
        }
        if (req.locationId() != null || req.location() != null) {
            e.setLocation(resolveLocation(req.locationId(), req.location()));
        }
        e.setUpdatedBy(currentUser.requireSubject());
        Map<String, Object> changed = diff(before, snapshot(e));
        if (!changed.isEmpty()) {
            audit.record(e, EventAuditAction.EVENT_UPDATED, changed, null);
        }
        return e;
    }

    @Transactional
    public Event publish(UUID id) {
        Event e = loadOwned(id);
        validator.assertPublishable(e);
        e.setStatus(EventStatus.PUBLISHED);
        audit.record(e, EventAuditAction.EVENT_PUBLISHED, null, null);
        domainEvents.publishEvent(new EventDomainEvent.Published(e.getId(), e.getPublicId()));
        return e;
    }

    @Transactional
    public Event cancel(UUID id, String reason) {
        Event e = loadOwned(id);
        validator.assertCancellable(e);
        e.setStatus(EventStatus.CANCELLED);
        audit.record(e, EventAuditAction.EVENT_CANCELLED, null, reason);
        domainEvents.publishEvent(new EventDomainEvent.Cancelled(e.getId(), e.getPublicId(), reason));
        return e;
    }

    @Transactional
    public Event complete(UUID id) {
        Event e = loadOwned(id);
        validator.assertCompletable(e);
        e.setStatus(EventStatus.COMPLETED);
        audit.record(e, EventAuditAction.EVENT_COMPLETED, null, null);
        domainEvents.publishEvent(new EventDomainEvent.Completed(e.getId(), e.getPublicId()));
        return e;
    }

    @Transactional(readOnly = true)
    public PublicationReadinessResponse readiness(UUID id) {
        return readiness.check(loadOwned(id));
    }

    @Transactional(readOnly = true)
    public Page<EventAuditResponse> listAudit(UUID id, Pageable pageable) {
        loadOwned(id); // ownership / existence
        return audit.list(id, pageable);
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

    /** Captures the audited scalar fields of an event for before/after diffing. */
    private Map<String, Object> snapshot(Event e) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", e.getName());
        s.put("slug", e.getSlug());
        s.put("visibility", e.getVisibility());
        s.put("attendanceMode", e.getAttendanceMode());
        s.put("commercialMode", e.getCommercialMode());
        s.put("paymentProvider", e.getPaymentProvider());
        s.put("currency", e.getCurrency());
        s.put("timezone", e.getTimezone());
        s.put("startsAt", e.getStartsAt());
        s.put("endsAt", e.getEndsAt());
        s.put("capacity", e.getCapacity());
        return s;
    }

    /** Builds a {field:{from,to}} diff of the changed keys between two snapshots. */
    private Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changed = new LinkedHashMap<>();
        before.forEach((key, oldValue) -> {
            Object newValue = after.get(key);
            if (!java.util.Objects.equals(oldValue, newValue)) {
                Map<String, Object> fromTo = new LinkedHashMap<>();
                fromTo.put("from", stringOrNull(oldValue));
                fromTo.put("to", stringOrNull(newValue));
                changed.put(key, fromTo);
            }
        });
        return changed;
    }

    private static Object stringOrNull(Object v) {
        return v == null ? null : v.toString();
    }

    private String normalizeCurrency(String currency) {
        String c = currency != null ? currency : defaultCurrency;
        return c.toUpperCase(Locale.ROOT);
    }

    /** Validates an IANA zone ID and returns its canonical form (§16). */
    private String normalizeTimezone(String raw) {
        try {
            return ZoneId.of(raw).getId();
        } catch (DateTimeException ex) {
            throw ApiException.validation("Invalid timezone: " + raw + " (expected an IANA zone ID)");
        }
    }

    /**
     * A free event never processes payments ({@code NONE}); a paid event uses the
     * requested provider, defaulting to the only wired adapter ({@code MOCK}) until
     * real gateways and capability approval land in a later phase.
     */
    private PaymentProvider resolveProvider(CommercialMode mode, PaymentProvider requested) {
        if (mode == CommercialMode.FREE) {
            return PaymentProvider.NONE;
        }
        return (requested == null || requested == PaymentProvider.NONE) ? PaymentProvider.MOCK : requested;
    }

    /** Slug may only change while DRAFT (§5.3); normalized and checked for uniqueness. */
    private void applySlugChange(Event e, String requestedSlug) {
        if (e.getStatus() != EventStatus.DRAFT) {
            throw ApiException.invalidState("Slug can only be changed while the event is a draft");
        }
        String slug = slugify(requestedSlug);
        if (!slug.equals(e.getSlug()) && events.existsBySlug(slug)) {
            throw ApiException.of(ErrorCode.CONFLICT, "Slug already in use: " + slug);
        }
        e.setSlug(slug);
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
