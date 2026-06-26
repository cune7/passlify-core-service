package com.passlify.core.event.dto;

import com.passlify.core.event.Visibility;
import com.passlify.core.payment.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Create an event. {@code slug} and {@code status=DRAFT} are server-assigned.
 * Provide either {@code locationId} (reuse) or {@code location} (create-or-reuse),
 * or neither. {@code currency}/{@code paymentProvider} fall back to defaults.
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 2048) String coverImageUrl,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        UUID eventTypeId,
        UUID locationId,
        @Valid LocationDto location,
        @PositiveOrZero Integer capacity,
        List<String> tags,
        @Size(min = 3, max = 3) String currency,
        Visibility visibility,
        PaymentProvider paymentProvider) {
}
