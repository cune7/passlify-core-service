package com.passlify.core.event;

/**
 * Fine-grained event actions gated by the collaborator permission matrix
 * (EVENT_DOMAIN_SPEC §13.2). Each event operation requires one of these; the
 * caller's effective {@link EventRole} determines whether it is granted.
 */
public enum EventCapability {
    VIEW,
    EDIT_DETAILS,
    EDIT_COMMERCIAL,
    MANAGE_LIFECYCLE,
    CONFIGURE_TICKETS,
    VIEW_REPORTS,
    SCAN,
    MANAGE_COLLABORATORS,
    TRANSFER_OWNERSHIP
}
