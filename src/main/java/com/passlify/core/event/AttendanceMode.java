package com.passlify.core.event;

/**
 * How attendees participate (EVENT_DOMAIN_SPEC §14). Drives publication-readiness:
 * {@code IN_PERSON} requires a physical location, {@code ONLINE} requires online
 * access configuration, {@code HYBRID} requires both.
 */
public enum AttendanceMode {
    IN_PERSON,
    ONLINE,
    HYBRID
}
