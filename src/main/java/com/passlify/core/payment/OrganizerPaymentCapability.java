package com.passlify.core.payment;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * An organization's admin-approved capability to process payments through a specific
 * provider (EVENT_DOMAIN_SPEC §10.2). The organizer cannot self-assign providers; an
 * ADMIN grants and manages these. A paid event may only publish on a provider for
 * which its organization holds a usable (ACTIVE, in-window, currency-covering) capability.
 */
@Getter
@Setter
@Entity
@Table(name = "organizer_payment_capability")
public class OrganizerPaymentCapability extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapabilityStatus status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_currencies", columnDefinition = "text[]")
    private List<String> allowedCurrencies;

    @Column(name = "merchant_configuration_reference", length = 255)
    private String merchantConfigurationReference;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** ACTIVE and within any validity window (currency not considered). */
    public boolean isActiveNow() {
        if (status != CapabilityStatus.ACTIVE) {
            return false;
        }
        Instant now = Instant.now();
        return (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || !now.isAfter(validUntil));
    }

    public boolean coversCurrency(String currency) {
        return allowedCurrencies != null
                && allowedCurrencies.contains(currency == null ? null : currency.toUpperCase(Locale.ROOT));
    }

    /** Usable now: ACTIVE, within any validity window, and covering the given currency. */
    public boolean isUsableFor(String currency) {
        return isActiveNow() && coversCurrency(currency);
    }
}
