package com.passlify.core.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cancel an event. A reason is required and recorded in the audit trail (§24.2). */
public record CancelEventRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
