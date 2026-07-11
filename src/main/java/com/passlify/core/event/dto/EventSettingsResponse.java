package com.passlify.core.event.dto;

import com.passlify.core.event.EventSettings;
import java.util.List;
import java.util.UUID;

/** Organizer-facing view of an event's structured settings. */
public record EventSettingsResponse(
        UUID eventId,
        Integer minimumAge,
        boolean ticketsAvailableAtEntrance,
        boolean visitorCountryRestrictionEnabled,
        List<String> allowedVisitorCountryCodes,
        boolean multipleEntryAllowed,
        boolean peopleWithDisabilitiesFreeEntry,
        Integer childrenFreeEntryAge,
        String termsHtml,
        String additionalRulesHtml) {

    public static EventSettingsResponse from(EventSettings s) {
        return new EventSettingsResponse(
                s.getEventId(),
                s.getMinimumAge(),
                s.isTicketsAvailableAtEntrance(),
                s.isVisitorCountryRestrictionEnabled(),
                s.getAllowedVisitorCountryCodes(),
                s.isMultipleEntryAllowed(),
                s.isPeopleWithDisabilitiesFreeEntry(),
                s.getChildrenFreeEntryAge(),
                s.getTermsHtml(),
                s.getAdditionalRulesHtml());
    }
}
