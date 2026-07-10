package com.passlify.core.organization;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import org.springframework.stereotype.Component;

/**
 * All validation rules for organization profiles. Injected into
 * {@link OrganizationService} (and {@code EventValidator} for the paid-event guard),
 * keeping every organization precondition in one place.
 */
@Component
public class OrganizationValidator {

    private final OrganizationRepository organizations;

    public OrganizationValidator(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** A COMPANY profile must carry the legal/billing fields and valid identifiers. */
    public void validateForUpsert(UpsertOrganizationRequest req) {
        if (req.kind() != OrganizationKind.COMPANY) {
            return;
        }
        if (isBlank(req.legalName()) || isBlank(req.vatPib()) || isBlank(req.registrationNo())
                || isBlank(req.addressLine()) || isBlank(req.city()) || isBlank(req.country())) {
            throw ApiException.validation(
                    "A COMPANY profile needs legalName, vatPib, registrationNo, addressLine, city and country.");
        }
        // Serbian companies (the default) must have a 9-digit PIB and 8-digit matični broj;
        // other countries are only required to be non-blank until per-country rules land.
        if (VatNumbers.isSerbianCountry(req.country())) {
            if (!VatNumbers.isValidSerbianPib(req.vatPib())) {
                throw ApiException.validation(
                        "Invalid PIB: a Serbian tax id must be 9 digits (an optional 'RS' prefix is allowed).");
            }
            if (!VatNumbers.isValidSerbianRegistrationNo(req.registrationNo())) {
                throw ApiException.validation(
                        "Invalid matični broj (MBR): a Serbian company registration number must be 8 digits.");
            }
        }
    }

    /**
     * Guard for selling paid events: the owner must have a billable COMPANY profile.
     * Throws INVALID_STATE with an actionable message otherwise.
     */
    public void assertCanSellPaidEvents(String ownerId) {
        Organization o = organizations.findByOwnerId(ownerId).orElse(null);
        if (o == null || !o.isBillableCompany()) {
            throw ApiException.invalidState(
                    "Paid events require a complete company profile (kind=COMPANY with legal name, "
                            + "VAT/PIB, registration number and address). Set it via PUT /api/v1/me/organization.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
