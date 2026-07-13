package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.event.dto.AccessGrantResponse;
import com.passlify.core.event.dto.CreateAccessGrantRequest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages shareable access grants for PRIVATE events (EVENT_DOMAIN_SPEC §8). Creating,
 * listing and revoking require the event {@code MANAGE_COLLABORATORS} capability;
 * {@link #hasValidAccess} is the public bearer-token check used by the catalog and
 * checkout to let invitees view/buy a private event.
 */
@Service
public class EventAccessService {

    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final EventAccessGrantRepository grants;
    private final EventRepository events;
    private final EventAuthorization authorization;
    private final CurrentUser currentUser;
    private final SecureRandom random = new SecureRandom();

    public EventAccessService(EventAccessGrantRepository grants,
                              EventRepository events,
                              EventAuthorization authorization,
                              CurrentUser currentUser) {
        this.grants = grants;
        this.events = events;
        this.authorization = authorization;
        this.currentUser = currentUser;
    }

    @Transactional
    public AccessGrantResponse create(UUID eventId, CreateAccessGrantRequest req) {
        authorization.require(loadEvent(eventId), EventCapability.MANAGE_COLLABORATORS);
        EventAccessGrant grant = new EventAccessGrant();
        grant.setEventId(eventId);
        grant.setToken(newToken());
        grant.setLabel(req.label());
        grant.setCreatedBy(currentUser.requireSubject());
        return AccessGrantResponse.from(grants.save(grant));
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> list(UUID eventId) {
        authorization.require(loadEvent(eventId), EventCapability.MANAGE_COLLABORATORS);
        return grants.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(AccessGrantResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(UUID eventId, UUID grantId) {
        authorization.require(loadEvent(eventId), EventCapability.MANAGE_COLLABORATORS);
        EventAccessGrant grant = grants.findByIdAndEventId(grantId, eventId)
                .orElseThrow(() -> ApiException.notFound("Access grant not found: " + grantId));
        grant.setRevoked(true);
    }

    /** True if {@code token} is a live (non-revoked) grant for this event. */
    @Transactional(readOnly = true)
    public boolean hasValidAccess(UUID eventId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return grants.findByToken(token)
                .filter(g -> !g.isRevoked() && g.getEventId().equals(eventId))
                .isPresent();
    }

    private Event loadEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + eventId));
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
