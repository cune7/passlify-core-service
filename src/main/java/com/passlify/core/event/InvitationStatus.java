package com.passlify.core.event;

/** Lifecycle of a collaborator invitation (EVENT_DOMAIN_SPEC §13.3). */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
}
