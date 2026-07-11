package com.passlify.core.payment;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.organization.Organization;
import com.passlify.core.organization.OrganizationRepository;
import com.passlify.core.payment.dto.GrantCapabilityRequest;
import com.passlify.core.payment.dto.PaymentCapabilityResponse;
import com.passlify.core.payment.dto.UpdateCapabilityRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management and lookup of {@link OrganizerPaymentCapability} (EVENT_DOMAIN_SPEC
 * §10). Admins grant/suspend/revoke; the event publish gate queries {@link #isUsable}.
 */
@Service
public class PaymentCapabilityService {

    private final OrganizerPaymentCapabilityRepository capabilities;
    private final OrganizationRepository organizations;
    private final CurrentUser currentUser;

    public PaymentCapabilityService(OrganizerPaymentCapabilityRepository capabilities,
                                    OrganizationRepository organizations,
                                    CurrentUser currentUser) {
        this.capabilities = capabilities;
        this.organizations = organizations;
        this.currentUser = currentUser;
    }

    /** Admin: grant (or replace) an ACTIVE capability for an organization + provider. */
    @Transactional
    public PaymentCapabilityResponse grant(UUID organizationId, GrantCapabilityRequest req) {
        if (req.provider() == PaymentProvider.NONE) {
            throw ApiException.validation("NONE is not a grantable provider");
        }
        if (!organizations.existsById(organizationId)) {
            throw ApiException.notFound("Organization not found: " + organizationId);
        }
        OrganizerPaymentCapability c = capabilities
                .findByOrganizationIdAndProvider(organizationId, req.provider())
                .orElseGet(() -> {
                    OrganizerPaymentCapability created = new OrganizerPaymentCapability();
                    created.setOrganizationId(organizationId);
                    created.setProvider(req.provider());
                    return created;
                });
        c.setStatus(CapabilityStatus.ACTIVE);
        c.setAllowedCurrencies(normalizeCurrencies(req.allowedCurrencies()));
        c.setMerchantConfigurationReference(req.merchantConfigurationReference());
        c.setValidFrom(req.validFrom());
        c.setValidUntil(req.validUntil());
        c.setApprovedBy(currentUser.requireSubject());
        c.setApprovedAt(Instant.now());
        return PaymentCapabilityResponse.from(capabilities.save(c));
    }

    @Transactional(readOnly = true)
    public List<PaymentCapabilityResponse> listForOrganization(UUID organizationId) {
        return capabilities.findByOrganizationIdOrderByProviderAsc(organizationId).stream()
                .map(PaymentCapabilityResponse::from)
                .toList();
    }

    /** Admin: change a capability's status (suspend/revoke/re-activate) and/or currencies. */
    @Transactional
    public PaymentCapabilityResponse updateStatus(UUID capabilityId, UpdateCapabilityRequest req) {
        OrganizerPaymentCapability c = capabilities.findById(capabilityId)
                .orElseThrow(() -> ApiException.notFound("Capability not found: " + capabilityId));
        c.setStatus(req.status());
        if (req.allowedCurrencies() != null) {
            c.setAllowedCurrencies(normalizeCurrencies(req.allowedCurrencies()));
        }
        if (req.status() == CapabilityStatus.ACTIVE) {
            c.setApprovedBy(currentUser.requireSubject());
            c.setApprovedAt(Instant.now());
        }
        return PaymentCapabilityResponse.from(c);
    }

    /** Organizer: the capabilities of the caller's own organization. */
    @Transactional(readOnly = true)
    public List<PaymentCapabilityResponse> listMine() {
        return organizations.findByOwnerId(currentUser.requireSubject())
                .map(Organization::getId)
                .map(this::listForOrganization)
                .orElseGet(List::of);
    }

    /** Publish-gate query: does the org hold a usable capability for this provider + currency? */
    @Transactional(readOnly = true)
    public boolean isUsable(UUID organizationId, PaymentProvider provider, String currency) {
        return find(organizationId, provider).map(c -> c.isUsableFor(currency)).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<OrganizerPaymentCapability> find(UUID organizationId, PaymentProvider provider) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return capabilities.findByOrganizationIdAndProvider(organizationId, provider);
    }

    private List<String> normalizeCurrencies(List<String> codes) {
        return codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
