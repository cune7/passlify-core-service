package com.passlify.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Online-access configuration for ONLINE / HYBRID events (EVENT_DOMAIN_SPEC §14.5),
 * one-to-one with {@link Event}. The join URL and instructions are sensitive: they
 * are managed here and must only be revealed to eligible attendees, never placed on
 * the public event entity. (Attendee-eligibility reveal is a later phase.)
 */
@Getter
@Setter
@Entity
@Table(name = "event_online_access")
public class EventOnlineAccess {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "public_url", length = 2048)
    private String publicUrl;

    @Column(name = "platform_name", length = 120)
    private String platformName;

    @Column(name = "access_instructions_html", columnDefinition = "text")
    private String accessInstructionsHtml;

    @Column(name = "reveal_only_after_registration", nullable = false)
    private boolean revealOnlyAfterRegistration = true;

    @Column(name = "reveal_at")
    private Instant revealAt;

    /** Online access is considered configured for publication once a join URL exists. */
    public boolean isConfigured() {
        return publicUrl != null && !publicUrl.isBlank();
    }
}
