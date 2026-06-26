package com.passlify.core.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inline venue payload — used to create-or-reuse a location, and in responses. */
public record LocationDto(
        @NotBlank @Size(max = 255) String venueName,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(min = 2, max = 2) String country,
        @NotBlank @Size(max = 20) String postalCode) {
}
