package com.passlify.core.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Admin override of an event's auto-completion grace (hours after {@code endsAt}).
 * Null clears the override, reverting to the platform default.
 */
public record SetAutoCompleteGraceRequest(
        @Min(0) @Max(720) Integer graceHours) {
}
