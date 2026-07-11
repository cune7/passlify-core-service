package com.passlify.core.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerPaymentCapabilityRepository
        extends JpaRepository<OrganizerPaymentCapability, UUID> {

    List<OrganizerPaymentCapability> findByOrganizationIdOrderByProviderAsc(UUID organizationId);

    Optional<OrganizerPaymentCapability> findByOrganizationIdAndProvider(
            UUID organizationId, PaymentProvider provider);
}
