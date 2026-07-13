package com.passlify.core.event.dto;

import com.passlify.core.event.EventContact;

/** Organizer-facing view of an event's contact and social links (§18). */
public record EventContactResponse(
        String email,
        String phone,
        String websiteUrl,
        String facebookUrl,
        String instagramUrl,
        String youtubeUrl,
        String linkedinUrl,
        String tiktokUrl,
        String xUrl,
        boolean showEmail,
        boolean showPhone,
        boolean showWebsite,
        boolean showSocial) {

    public static EventContactResponse from(EventContact c) {
        if (c == null) {
            return new EventContactResponse(null, null, null, null, null, null, null, null, null,
                    false, false, true, true);
        }
        return new EventContactResponse(
                c.getEmail(),
                c.getPhone(),
                c.getWebsiteUrl(),
                c.getFacebookUrl(),
                c.getInstagramUrl(),
                c.getYoutubeUrl(),
                c.getLinkedinUrl(),
                c.getTiktokUrl(),
                c.getXUrl(),
                c.isShowEmail(),
                c.isShowPhone(),
                c.isShowWebsite(),
                c.isShowSocial());
    }
}
