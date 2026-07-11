package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.common.security.CurrentUser;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves a caller's effective role on an event and enforces the collaborator
 * permission matrix (EVENT_DOMAIN_SPEC §13.2). The event owner is treated as
 * {@link EventRole#OWNER}; an ADMIN bypasses the matrix entirely.
 *
 * <p>A caller who is neither owner, admin, nor an accepted collaborator gets a 404
 * (existence is not leaked); a participant lacking the capability gets a 403.
 */
@Component
public class EventAuthorization {

    private static final Map<EventRole, EnumSet<EventCapability>> MATRIX = buildMatrix();

    private final EventCollaboratorRepository collaborators;
    private final CurrentUser currentUser;

    public EventAuthorization(EventCollaboratorRepository collaborators, CurrentUser currentUser) {
        this.collaborators = collaborators;
        this.currentUser = currentUser;
    }

    private static Map<EventRole, EnumSet<EventCapability>> buildMatrix() {
        Map<EventRole, EnumSet<EventCapability>> m = new EnumMap<>(EventRole.class);
        m.put(EventRole.OWNER, EnumSet.allOf(EventCapability.class));
        m.put(EventRole.MANAGER, EnumSet.of(
                EventCapability.VIEW, EventCapability.EDIT_DETAILS, EventCapability.EDIT_COMMERCIAL,
                EventCapability.MANAGE_LIFECYCLE, EventCapability.CONFIGURE_TICKETS,
                EventCapability.VIEW_REPORTS, EventCapability.SCAN, EventCapability.MANAGE_PAYMENTS,
                EventCapability.MANAGE_COLLABORATORS));
        m.put(EventRole.EDITOR, EnumSet.of(
                EventCapability.VIEW, EventCapability.EDIT_DETAILS,
                EventCapability.CONFIGURE_TICKETS, EventCapability.VIEW_REPORTS));
        m.put(EventRole.VIEWER, EnumSet.of(EventCapability.VIEW, EventCapability.VIEW_REPORTS));
        m.put(EventRole.CHECK_IN_OPERATOR, EnumSet.of(EventCapability.VIEW, EventCapability.SCAN));
        return m;
    }

    /** The caller's role on this event, or null if they are not a participant. */
    public EventRole effectiveRole(Event event) {
        String subject = currentUser.subject().orElse(null);
        if (subject == null) {
            return null;
        }
        if (subject.equals(event.getOrganizerId())) {
            return EventRole.OWNER;
        }
        return collaborators.findByEventIdAndUserId(event.getId(), subject)
                .filter(c -> c.getInvitationStatus() == InvitationStatus.ACCEPTED)
                .map(EventCollaborator::getRole)
                .orElse(null);
    }

    /** Non-throwing capability check (ADMIN always true). */
    public boolean has(Event event, EventCapability capability) {
        if (currentUser.isAdmin()) {
            return true;
        }
        EventRole role = effectiveRole(event);
        return role != null && MATRIX.getOrDefault(role, EnumSet.noneOf(EventCapability.class))
                .contains(capability);
    }

    /** Enforces a capability: 404 for non-participants, 403 for insufficient role. */
    public void require(Event event, EventCapability capability) {
        if (currentUser.isAdmin()) {
            return;
        }
        EventRole role = effectiveRole(event);
        if (role == null) {
            throw ApiException.notFound("Event not found: " + event.getId());
        }
        if (!MATRIX.getOrDefault(role, EnumSet.noneOf(EventCapability.class)).contains(capability)) {
            throw ApiException.of(ErrorCode.FORBIDDEN,
                    "Your role (" + role + ") cannot perform this action (" + capability + ")");
        }
    }
}
