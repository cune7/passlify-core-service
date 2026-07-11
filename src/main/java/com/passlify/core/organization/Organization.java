package com.passlify.core.organization;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An organizer's business profile. Owned 1:1 by a Keycloak user ({@code ownerId}
 * = sub). INDIVIDUAL is enough for free events; COMPANY (with legal fields) is
 * required to publish paid events — see {@link #isBillableCompany()}.
 */
@Getter
@Setter
@Entity
@Table(name = "organization")
public class Organization extends BaseEntity {

    @Column(name = "owner_id", nullable = false, unique = true, length = 64)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationKind kind = OrganizationKind.INDIVIDUAL;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "vat_pib", length = 32)
    private String vatPib;

    @Column(name = "registration_no", length = 32)
    private String registrationNo;

    @Column(name = "address_line", length = 512)
    private String addressLine;

    @Column(length = 120)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 2)
    private String country;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    /** Bank account for MANUAL (offline / bank-transfer) payouts — shown on payment instructions. */
    @Column(name = "bank_account_number", length = 64)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 255)
    private String bankAccountHolder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * True when this org can sell paid, B2B events: a COMPANY with the legal and
     * address fields required for invoicing filled in.
     */
    public boolean isBillableCompany() {
        return kind == OrganizationKind.COMPANY
                && isFilled(legalName)
                && isFilled(vatPib)
                && isFilled(registrationNo)
                && isFilled(addressLine)
                && isFilled(city)
                && isFilled(country);
    }

    private static boolean isFilled(String s) {
        return s != null && !s.isBlank();
    }
}
