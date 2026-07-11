package com.passlify.core.payment.dto;

import jakarta.validation.constraints.Positive;

/**
 * Organizer/manager (or admin) refund request. Omit {@code amountMinor} for a full
 * refund of the remaining balance; provide it for a partial refund. {@code reason} is
 * optional context.
 */
public record RefundOrderRequest(
        @Positive Long amountMinor,
        String reason) {
}
