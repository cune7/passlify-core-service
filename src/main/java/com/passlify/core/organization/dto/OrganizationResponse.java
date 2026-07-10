package com.passlify.core.organization.dto;

import com.passlify.core.organization.Organization;
import com.passlify.core.organization.OrganizationKind;
import java.time.Instant;
import java.util.UUID;

/** The caller's (or, for admins, any) organization profile. */
public record OrganizationResponse(
        UUID id,
        String ownerId,
        OrganizationKind kind,
        String displayName,
        String legalName,
        String vatPib,
        String registrationNo,
        String addressLine,
        String city,
        String postalCode,
        String country,
        String contactEmail,
        boolean billableCompany,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganizationResponse from(Organization o) {
        return new OrganizationResponse(
                o.getId(),
                o.getOwnerId(),
                o.getKind(),
                o.getDisplayName(),
                o.getLegalName(),
                o.getVatPib(),
                o.getRegistrationNo(),
                o.getAddressLine(),
                o.getCity(),
                o.getPostalCode(),
                o.getCountry(),
                o.getContactEmail(),
                o.isBillableCompany(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }
}
