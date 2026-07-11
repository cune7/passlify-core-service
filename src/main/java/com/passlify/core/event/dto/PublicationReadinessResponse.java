package com.passlify.core.event.dto;

import java.util.List;

/**
 * Result of the publication-readiness check (EVENT_DOMAIN_SPEC §23.5): whether the
 * event can be published, and the structured list of what is still missing. Powers
 * the organizer's pre-publish checklist.
 */
public record PublicationReadinessResponse(boolean ready, List<Violation> violations) {

    public record Violation(String code, String field, String message) {
    }
}
