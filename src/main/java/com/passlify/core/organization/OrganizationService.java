package com.passlify.core.organization;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organizer business profiles. Every organizer has exactly one {@link Organization}
 * (1:1 with the Keycloak sub), auto-created as INDIVIDUAL on their first event and
 * upgradeable to COMPANY. A paid event may only be published by a
 * {@link Organization#isBillableCompany() billable company}.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final OrganizationValidator validator;
    private final CurrentUser currentUser;

    public OrganizationService(OrganizationRepository organizations,
                               OrganizationValidator validator,
                               CurrentUser currentUser) {
        this.organizations = organizations;
        this.validator = validator;
        this.currentUser = currentUser;
    }

    /**
     * Returns the caller's org, creating a minimal INDIVIDUAL one (named from the
     * token) if none exists yet. Used on event creation so every event has an owner
     * organization.
     */
    @Transactional
    public Organization getOrCreateForCurrentUser() {
        String ownerId = currentUser.requireSubject();
        return organizations.findByOwnerId(ownerId).orElseGet(() -> {
            Organization o = new Organization();
            o.setOwnerId(ownerId);
            o.setKind(OrganizationKind.INDIVIDUAL);
            o.setDisplayName(currentUser.displayName());
            o.setContactEmail(currentUser.email().orElse(null));
            return organizations.save(o);
        });
    }

    @Transactional(readOnly = true)
    public Organization getMine() {
        String ownerId = currentUser.requireSubject();
        return organizations.findByOwnerId(ownerId)
                .orElseThrow(() -> ApiException.notFound("No organization profile yet"));
    }

    /** Create or update the caller's organization profile. */
    @Transactional
    public Organization upsertMine(UpsertOrganizationRequest req) {
        validator.validateForUpsert(req);
        String ownerId = currentUser.requireSubject();
        Organization o = organizations.findByOwnerId(ownerId).orElseGet(() -> {
            Organization created = new Organization();
            created.setOwnerId(ownerId);
            return created;
        });
        String country = req.country() != null ? req.country().toUpperCase(Locale.ROOT) : null;
        o.setKind(req.kind());
        o.setDisplayName(req.displayName());
        o.setLegalName(req.legalName());
        // Store the canonical 9-digit PIB for Serbian companies (strip RS/spaces).
        o.setVatPib(VatNumbers.isSerbianCountry(country) ? VatNumbers.normalizePib(req.vatPib()) : req.vatPib());
        o.setRegistrationNo(req.registrationNo());
        o.setAddressLine(req.addressLine());
        o.setCity(req.city());
        o.setPostalCode(req.postalCode());
        o.setCountry(country);
        o.setContactEmail(req.contactEmail());
        o.setBankAccountNumber(req.bankAccountNumber());
        o.setBankAccountHolder(req.bankAccountHolder());
        return organizations.save(o);
    }

    @Transactional(readOnly = true)
    public Page<Organization> listAll(Pageable pageable) {
        return organizations.findAll(pageable);
    }
}
