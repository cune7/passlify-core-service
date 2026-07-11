package com.passlify.core.event.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Configure online access for an ONLINE / HYBRID event (EVENT_DOMAIN_SPEC §14.5). */
public record EventOnlineAccessRequest(
        @Size(max = 2048) String publicUrl,
        @Size(max = 120) String platformName,
        String accessInstructionsHtml,
        Boolean revealOnlyAfterRegistration,
        Instant revealAt) {
}
