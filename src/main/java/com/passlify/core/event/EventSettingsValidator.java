package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.EventSettingsRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates structured event settings (EVENT_DOMAIN_SPEC §20). Country restriction
 * is rejected outright when enabled without any allowed country (US-EVT-012), rather
 * than only warned about at publication.
 */
@Component
public class EventSettingsValidator {

    public void validate(EventSettingsRequest req) {
        boolean restrictionEnabled = Boolean.TRUE.equals(req.visitorCountryRestrictionEnabled());
        List<String> codes = req.allowedVisitorCountryCodes();
        if (restrictionEnabled && (codes == null || codes.isEmpty())) {
            throw ApiException.validation(
                    "At least one allowed visitor country is required when country restriction is enabled");
        }
        if (codes != null) {
            for (String code : codes) {
                if (code == null || !code.trim().matches("[A-Za-z]{2}")) {
                    throw ApiException.validation(
                            "Visitor country codes must be ISO-3166-1 alpha-2 (got: " + code + ")");
                }
            }
        }
    }

    /** Normalizes country codes to uppercase; returns null when the list is empty. */
    public List<String> normalizeCountryCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return codes.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
