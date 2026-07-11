package com.passlify.core.event;

import com.passlify.core.common.text.HtmlSanitizationService;
import com.passlify.core.event.dto.EventSettingsRequest;
import com.passlify.core.event.dto.EventSettingsResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and replaces an event's structured {@link EventSettings}. Ownership is
 * enforced by delegating the event load to {@link EventService}; the settings row
 * is created lazily on first write (EVENT_DOMAIN_SPEC §20).
 */
@Service
public class EventSettingsService {

    private final EventService events;
    private final EventSettingsRepository settings;
    private final EventSettingsValidator validator;
    private final HtmlSanitizationService htmlSanitizer;

    public EventSettingsService(EventService events,
                                EventSettingsRepository settings,
                                EventSettingsValidator validator,
                                HtmlSanitizationService htmlSanitizer) {
        this.events = events;
        this.settings = settings;
        this.validator = validator;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Transactional(readOnly = true)
    public EventSettingsResponse get(UUID eventId) {
        events.getOwned(eventId); // ownership / existence check (404 otherwise)
        return settings.findById(eventId)
                .map(EventSettingsResponse::from)
                .orElseGet(() -> defaults(eventId));
    }

    @Transactional
    public EventSettingsResponse update(UUID eventId, EventSettingsRequest req) {
        validator.validate(req);
        Event event = events.getForCapability(eventId, EventCapability.EDIT_DETAILS);

        EventSettings s = settings.findById(eventId).orElseGet(() -> {
            EventSettings created = new EventSettings();
            created.setEvent(event);
            return created;
        });

        s.setMinimumAge(req.minimumAge());
        s.setTicketsAvailableAtEntrance(Boolean.TRUE.equals(req.ticketsAvailableAtEntrance()));
        s.setVisitorCountryRestrictionEnabled(Boolean.TRUE.equals(req.visitorCountryRestrictionEnabled()));
        s.setAllowedVisitorCountryCodes(validator.normalizeCountryCodes(req.allowedVisitorCountryCodes()));
        s.setMultipleEntryAllowed(Boolean.TRUE.equals(req.multipleEntryAllowed()));
        s.setPeopleWithDisabilitiesFreeEntry(Boolean.TRUE.equals(req.peopleWithDisabilitiesFreeEntry()));
        s.setChildrenFreeEntryAge(req.childrenFreeEntryAge());
        s.setTermsHtml(htmlSanitizer.sanitizeHtml(req.termsHtml()));
        s.setAdditionalRulesHtml(htmlSanitizer.sanitizeHtml(req.additionalRulesHtml()));

        return EventSettingsResponse.from(settings.save(s));
    }

    private EventSettingsResponse defaults(UUID eventId) {
        return new EventSettingsResponse(eventId, null, false, false,
                List.of(), false, false, null, null, null);
    }
}
