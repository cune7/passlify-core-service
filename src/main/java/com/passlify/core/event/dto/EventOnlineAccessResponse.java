package com.passlify.core.event.dto;

import com.passlify.core.event.EventOnlineAccess;
import java.time.Instant;
import java.util.UUID;

/** Organizer-facing view of an event's online-access configuration. */
public record EventOnlineAccessResponse(
        UUID eventId,
        String publicUrl,
        String platformName,
        String accessInstructionsHtml,
        boolean revealOnlyAfterRegistration,
        Instant revealAt) {

    public static EventOnlineAccessResponse from(EventOnlineAccess a) {
        return new EventOnlineAccessResponse(
                a.getEventId(), a.getPublicUrl(), a.getPlatformName(),
                a.getAccessInstructionsHtml(), a.isRevealOnlyAfterRegistration(), a.getRevealAt());
    }
}
