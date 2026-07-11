package com.passlify.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Structured event conditions (EVENT_DOMAIN_SPEC §20), kept out of the free-text
 * description so they are queryable and enforceable. One-to-one with {@link Event}
 * sharing its primary key via {@code @MapsId}.
 *
 * <p>Most flags are informational in Phase 1 (they inform attendees) — notably
 * {@code multipleEntryAllowed}, which the scan module must be upgraded to honour
 * before it changes runtime behaviour.
 */
@Getter
@Setter
@Entity
@Table(name = "event_settings")
public class EventSettings {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    /** Minimum attendee age (inclusive), or null for no age restriction. */
    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "tickets_available_at_entrance", nullable = false)
    private boolean ticketsAvailableAtEntrance = false;

    @Column(name = "visitor_country_restriction_enabled", nullable = false)
    private boolean visitorCountryRestrictionEnabled = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_visitor_country_codes", columnDefinition = "text[]")
    private List<String> allowedVisitorCountryCodes;

    @Column(name = "multiple_entry_allowed", nullable = false)
    private boolean multipleEntryAllowed = false;

    @Column(name = "people_with_disabilities_free_entry", nullable = false)
    private boolean peopleWithDisabilitiesFreeEntry = false;

    /** Children strictly younger than this age enter free, or null if not offered. */
    @Column(name = "children_free_entry_age")
    private Integer childrenFreeEntryAge;

    @Column(name = "terms_html", columnDefinition = "text")
    private String termsHtml;

    @Column(name = "additional_rules_html", columnDefinition = "text")
    private String additionalRulesHtml;
}
