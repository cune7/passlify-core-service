package com.passlify.core.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Full replace of an event's structured settings (EVENT_DOMAIN_SPEC §20). Omitted
 * boolean flags default to false; {@code allowedVisitorCountryCodes} is required
 * (non-empty) when {@code visitorCountryRestrictionEnabled} is true.
 */
public record EventSettingsRequest(
        @Min(0) @Max(120) Integer minimumAge,
        Boolean ticketsAvailableAtEntrance,
        Boolean visitorCountryRestrictionEnabled,
        List<String> allowedVisitorCountryCodes,
        Boolean multipleEntryAllowed,
        Boolean peopleWithDisabilitiesFreeEntry,
        @Min(0) @Max(18) Integer childrenFreeEntryAge,
        String termsHtml,
        String additionalRulesHtml) {
}
