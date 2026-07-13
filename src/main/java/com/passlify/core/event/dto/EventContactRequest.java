package com.passlify.core.event.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Replace an event's contact and social links (EVENT_DOMAIN_SPEC §18). A PUT fully
 * replaces the contact block; omitted URL fields are cleared. At least one of
 * {@code email}, {@code phone} or {@code websiteUrl} must be present for the event
 * to become publishable (§18.2), but that is enforced at publish time, not here —
 * a draft may be saved incomplete. The {@code show*} flags default to the entity
 * defaults (email/phone hidden, website/social shown) when null.
 */
public record EventContactRequest(
        @Email @Size(max = 320) String email,
        @Size(max = 40) String phone,
        @Size(max = 2048) String websiteUrl,
        @Size(max = 2048) String facebookUrl,
        @Size(max = 2048) String instagramUrl,
        @Size(max = 2048) String youtubeUrl,
        @Size(max = 2048) String linkedinUrl,
        @Size(max = 2048) String tiktokUrl,
        @Size(max = 2048) String xUrl,
        Boolean showEmail,
        Boolean showPhone,
        Boolean showWebsite,
        Boolean showSocial) {
}
