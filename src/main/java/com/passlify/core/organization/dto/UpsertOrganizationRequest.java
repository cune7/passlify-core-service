package com.passlify.core.organization.dto;

import com.passlify.core.organization.OrganizationKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update the caller's organization profile.
 *
 * <p>{@code displayName} is always required (shown publicly as the organizer).
 * When {@code kind=COMPANY}, the legal/billing fields must be provided — they are
 * required to publish paid events (validated in the service, since which fields
 * are mandatory depends on {@code kind}).
 */
public record UpsertOrganizationRequest(
        @NotNull OrganizationKind kind,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 255) String legalName,
        @Size(max = 32) String vatPib,
        @Size(max = 32) String registrationNo,
        @Size(max = 512) String addressLine,
        @Size(max = 120) String city,
        @Size(max = 20) String postalCode,
        @Size(min = 2, max = 2) String country,
        @Email @Size(max = 320) String contactEmail) {
}
