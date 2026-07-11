package com.passlify.core.event.dto;

import com.passlify.core.event.AttendanceMode;
import com.passlify.core.event.CommercialMode;
import com.passlify.core.event.Visibility;
import com.passlify.core.payment.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partial update — only non-null fields are applied. {@code slug} is mutable only
 * while the event is {@code DRAFT}. {@code version}, when supplied, enables optimistic
 * concurrency: a stale value fails with 409 (§22.1).
 */
public record UpdateEventRequest(
        @Size(min = 3, max = 255) String name,
        @Size(min = 3, max = 120) String slug,
        String descriptionHtml,
        @Size(max = 2048) String coverImageUrl,
        Instant startsAt,
        Instant endsAt,
        @Size(max = 64) String timezone,
        AttendanceMode attendanceMode,
        CommercialMode commercialMode,
        UUID eventTypeId,
        UUID locationId,
        @Valid LocationDto location,
        @PositiveOrZero Integer capacity,
        List<String> tags,
        @Size(min = 3, max = 3) String currency,
        Visibility visibility,
        PaymentProvider paymentProvider,
        Long version) {
}
