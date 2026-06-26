package com.passlify.core.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByProviderSessionId(String providerSessionId);

    Optional<Payment> findByProviderIntentId(String providerIntentId);

    Optional<Payment> findByProviderChargeId(String providerChargeId);
}
