package com.passlify.core.payment.dto;

import com.passlify.core.payment.PaymentProvider;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * Admin grant of a payment capability to an organization (EVENT_DOMAIN_SPEC §10.3).
 * Creates or replaces the (organization, provider) capability as ACTIVE.
 */
public record GrantCapabilityRequest(
        @NotNull PaymentProvider provider,
        @NotEmpty List<String> allowedCurrencies,
        Instant validFrom,
        Instant validUntil,
        String merchantConfigurationReference) {
}
