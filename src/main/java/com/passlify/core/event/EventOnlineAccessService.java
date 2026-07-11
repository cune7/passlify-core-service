package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.text.HtmlSanitizationService;
import com.passlify.core.event.dto.EventOnlineAccessRequest;
import com.passlify.core.event.dto.EventOnlineAccessResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and replaces an event's {@link EventOnlineAccess}. Ownership is enforced via
 * {@link EventService}; the row is created lazily on first write.
 */
@Service
public class EventOnlineAccessService {

    private final EventService events;
    private final EventOnlineAccessRepository access;
    private final HtmlSanitizationService htmlSanitizer;

    public EventOnlineAccessService(EventService events,
                                    EventOnlineAccessRepository access,
                                    HtmlSanitizationService htmlSanitizer) {
        this.events = events;
        this.access = access;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Transactional(readOnly = true)
    public EventOnlineAccessResponse get(UUID eventId) {
        events.getOwned(eventId);
        return access.findById(eventId)
                .map(EventOnlineAccessResponse::from)
                .orElseGet(() -> new EventOnlineAccessResponse(eventId, null, null, null, true, null));
    }

    @Transactional
    public EventOnlineAccessResponse update(UUID eventId, EventOnlineAccessRequest req) {
        Event event = events.getOwned(eventId);
        if (req.publicUrl() != null && !req.publicUrl().isBlank()
                && !req.publicUrl().startsWith("https://")) {
            throw ApiException.validation("Online access URL must use https");
        }

        EventOnlineAccess a = access.findById(eventId).orElseGet(() -> {
            EventOnlineAccess created = new EventOnlineAccess();
            created.setEvent(event);
            return created;
        });
        a.setPublicUrl(blankToNull(req.publicUrl()));
        a.setPlatformName(blankToNull(req.platformName()));
        a.setAccessInstructionsHtml(htmlSanitizer.sanitizeHtml(req.accessInstructionsHtml()));
        a.setRevealOnlyAfterRegistration(
                req.revealOnlyAfterRegistration() == null || req.revealOnlyAfterRegistration());
        a.setRevealAt(req.revealAt());
        return EventOnlineAccessResponse.from(access.save(a));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
