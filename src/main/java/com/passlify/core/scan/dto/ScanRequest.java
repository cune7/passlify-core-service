package com.passlify.core.scan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A gate scan: the QR token, the event being scanned for, and an optional gate id. */
public record ScanRequest(
        @NotBlank String qrToken,
        @NotNull UUID eventId,
        @Size(max = 80) String gate) {
}
