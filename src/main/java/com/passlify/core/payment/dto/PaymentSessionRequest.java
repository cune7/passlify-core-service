package com.passlify.core.payment.dto;

import jakarta.validation.constraints.Size;

/** Optional success/cancel redirect overrides; defaults derive from the order's returnUrl. */
public record PaymentSessionRequest(
        @Size(max = 1024) String successUrl,
        @Size(max = 1024) String cancelUrl) {
}
