package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import com.passlify.core.payment.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * The thing people buy tickets for; the central aggregate root (EVENT_DOMAIN_SPEC §4).
 * Owned by a Keycloak user ({@code organizerId}) on behalf of an {@code organizationId}.
 *
 * <p>Three identifiers: the internal {@code id} (UUID PK, never exposed), the immutable
 * {@code publicId} (ULID, used in public URLs / QR / support), and the human-readable
 * {@code slug}. Focused sub-concepts (settings, online access, audit) live in their own
 * entities rather than bloating this one.
 */
@Getter
@Setter
@Entity
@Table(name = "event")
public class Event extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 26)
    private String publicId;

    @Column(unique = true, nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    /** Sanitized rich-text HTML (see HtmlSanitizationService). Safe for public render. */
    @Column(name = "description_html", columnDefinition = "text")
    private String descriptionHtml;

    /** Plain-text projection of {@link #descriptionHtml} for search/preview/SEO. */
    @Column(name = "description_plain_text", columnDefinition = "text")
    private String descriptionPlainText;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility = Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_mode", nullable = false, length = 20)
    private AttendanceMode attendanceMode = AttendanceMode.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_mode", nullable = false, length = 20)
    private CommercialMode commercialMode = CommercialMode.FREE;

    /** ISO-4217 default currency for ticket types under this event (e.g. RSD, EUR). */
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 20)
    private PaymentProvider paymentProvider = PaymentProvider.NONE;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** IANA zone ID the event's local times are expressed in (e.g. Europe/Belgrade). */
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    // EAGER (single-valued): responses are mapped after the service transaction
    // closes (open-in-view=false), so these must already be loaded. Listing pays a
    // small per-row cost — optimize later with an EntityGraph/fetch-join if needed.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "organizer_id", nullable = false, length = 64)
    private String organizerId;

    /** The owning organization (organizer's business profile). See OrganizationService. */
    @Column(name = "organization_id")
    private java.util.UUID organizationId;

    private Integer capacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    /** Event-specific contact and social links, distinct from the organization profile. */
    @Embedded
    private EventContact contact = new EventContact();

    /** Optimistic-lock guard: concurrent collaborator edits fail with 409 (§22.1). */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Safety net for the server-owned fields so an event persisted via a repository
     * (fixtures, internal flows) is never missing a required identity/audit value.
     * The normal API path in EventService sets these explicitly (actor-correct);
     * this only fills gaps, mirroring {@code BaseEntity}'s id assignment.
     */
    @PrePersist
    void applyEventDefaults() {
        if (publicId == null) {
            publicId = PublicIdGenerator.generate();
        }
        if (timezone == null) {
            timezone = "Europe/Belgrade";
        }
        if (createdBy == null) {
            createdBy = organizerId;
        }
        if (updatedBy == null) {
            updatedBy = organizerId;
        }
    }

    public boolean isPublished() {
        return status == EventStatus.PUBLISHED;
    }
}
