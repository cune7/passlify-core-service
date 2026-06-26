package com.passlify.core.event.dto;

import com.passlify.core.event.Visibility;
import com.passlify.core.payment.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Partial update — only non-null fields are applied. */
public record UpdateEventRequest(
        @Size(max = 255) String name,
        String description,
        @Size(max = 2048) String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        UUID eventTypeId,
        UUID locationId,
        @Valid LocationDto location,
        @PositiveOrZero Integer capacity,
        List<String> tags,
        @Size(min = 3, max = 3) String currency,
        Visibility visibility,
        PaymentProvider paymentProvider) {
}
