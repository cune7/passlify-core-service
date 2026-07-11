package com.passlify.core.event;

/**
 * Event-scoped collaborator role (EVENT_DOMAIN_SPEC §13.2). Access granted by these
 * roles applies to one event only — never to other events of the same owner/org.
 * {@code OWNER} is singular per event and assigned by ownership, not invitation.
 */
public enum EventRole {
    OWNER,
    MANAGER,
    EDITOR,
    VIEWER,
    CHECK_IN_OPERATOR
}
