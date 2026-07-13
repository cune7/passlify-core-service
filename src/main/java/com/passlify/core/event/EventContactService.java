package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.EventContactRequest;
import com.passlify.core.event.dto.EventContactResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and replaces an event's embedded {@link EventContact} (EVENT_DOMAIN_SPEC §18).
 * Ownership/authorization is delegated to {@link EventService}: reading needs VIEW,
 * editing needs EDIT_DETAILS. A PUT fully replaces the contact block. The
 * "at least one contact method" rule (§18.2) is a publish precondition
 * ({@link EventValidator#assertPublishable}), not a save-time constraint, so drafts
 * may be saved with partial contact details.
 */
@Service
public class EventContactService {

    private final EventService events;
    private final EventRepository eventRepository;

    public EventContactService(EventService events, EventRepository eventRepository) {
        this.events = events;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public EventContactResponse get(UUID eventId) {
        Event event = events.getOwned(eventId);
        return EventContactResponse.from(event.getContact());
    }

    @Transactional
    public EventContactResponse update(UUID eventId, EventContactRequest req) {
        Event event = events.getForCapability(eventId, EventCapability.EDIT_DETAILS);

        EventContact c = event.getContact();
        if (c == null) {
            c = new EventContact();
            event.setContact(c);
        }
        c.setEmail(blankToNull(req.email()));
        c.setPhone(blankToNull(req.phone()));
        c.setWebsiteUrl(requireHttpUrl("websiteUrl", req.websiteUrl()));
        c.setFacebookUrl(requireHttpUrl("facebookUrl", req.facebookUrl()));
        c.setInstagramUrl(requireHttpUrl("instagramUrl", req.instagramUrl()));
        c.setYoutubeUrl(requireHttpUrl("youtubeUrl", req.youtubeUrl()));
        c.setLinkedinUrl(requireHttpUrl("linkedinUrl", req.linkedinUrl()));
        c.setTiktokUrl(requireHttpUrl("tiktokUrl", req.tiktokUrl()));
        c.setXUrl(requireHttpUrl("xUrl", req.xUrl()));
        c.setShowEmail(req.showEmail() != null ? req.showEmail() : false);
        c.setShowPhone(req.showPhone() != null ? req.showPhone() : false);
        c.setShowWebsite(req.showWebsite() != null ? req.showWebsite() : true);
        c.setShowSocial(req.showSocial() != null ? req.showSocial() : true);

        return EventContactResponse.from(eventRepository.save(event).getContact());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String requireHttpUrl(String field, String raw) {
        String url = blankToNull(raw);
        if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
            throw ApiException.validation(field + " must be an absolute http(s) URL");
        }
        return url;
    }
}
