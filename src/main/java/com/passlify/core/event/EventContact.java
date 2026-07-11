package com.passlify.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * Event-specific contact and social links (EVENT_DOMAIN_SPEC §18), embedded in the
 * {@code event} row. Modeled separately from the organization profile because each
 * event may have different operational contacts. The {@code show*} flags let the
 * organizer choose which fields appear on the public page; a private operational
 * email must not become public by default.
 */
@Getter
@Setter
@Embeddable
public class EventContact {

    @Column(name = "contact_email", length = 320)
    private String email;

    @Column(name = "contact_phone", length = 40)
    private String phone;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "facebook_url", length = 2048)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 2048)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 2048)
    private String youtubeUrl;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Column(name = "tiktok_url", length = 2048)
    private String tiktokUrl;

    @Column(name = "x_url", length = 2048)
    private String xUrl;

    @Column(name = "contact_show_email", nullable = false)
    private boolean showEmail = false;

    @Column(name = "contact_show_phone", nullable = false)
    private boolean showPhone = false;

    @Column(name = "contact_show_website", nullable = false)
    private boolean showWebsite = true;

    @Column(name = "contact_show_social", nullable = false)
    private boolean showSocial = true;

    /** True when at least one contact method is present (publication requirement §18.2). */
    public boolean hasAnyMethod() {
        return notBlank(email) || notBlank(phone) || notBlank(websiteUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
