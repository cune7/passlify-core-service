package com.passlify.core.payment.dto;

import com.passlify.core.payment.CapabilityStatus;
import com.passlify.core.payment.OrganizerPaymentCapability;
import com.passlify.core.payment.PaymentProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin/organizer view of a payment capability. */
public record PaymentCapabilityResponse(
        UUID id,
        UUID organizationId,
        PaymentProvider provider,
        CapabilityStatus status,
        List<String> allowedCurrencies,
        String merchantConfigurationReference,
        Instant validFrom,
        Instant validUntil,
        String approvedBy,
        Instant approvedAt) {

    public static PaymentCapabilityResponse from(OrganizerPaymentCapability c) {
        return new PaymentCapabilityResponse(
                c.getId(), c.getOrganizationId(), c.getProvider(), c.getStatus(),
                c.getAllowedCurrencies(), c.getMerchantConfigurationReference(),
                c.getValidFrom(), c.getValidUntil(), c.getApprovedBy(), c.getApprovedAt());
    }
}
