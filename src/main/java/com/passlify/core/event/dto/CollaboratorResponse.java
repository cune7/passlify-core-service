package com.passlify.core.event.dto;

import com.passlify.core.event.EventCollaborator;
import com.passlify.core.event.EventRole;
import com.passlify.core.event.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Organizer-facing view of an event collaborator / invitation. {@code acceptToken} is
 * populated only in the response to a fresh invite (so it can be shared/sent); it is
 * null elsewhere.
 */
public record CollaboratorResponse(
        UUID id,
        UUID eventId,
        String userId,
        String email,
        EventRole role,
        InvitationStatus invitationStatus,
        String invitedBy,
        Instant invitedAt,
        Instant expiresAt,
        Instant acceptedAt,
        String acceptToken) {

    public static CollaboratorResponse from(EventCollaborator c) {
        return withToken(c, null);
    }

    public static CollaboratorResponse withToken(EventCollaborator c, String acceptToken) {
        return new CollaboratorResponse(
                c.getId(), c.getEventId(), c.getUserId(), c.getEmail(), c.getRole(),
                c.getInvitationStatus(), c.getInvitedBy(), c.getInvitedAt(),
                c.getExpiresAt(), c.getAcceptedAt(), acceptToken);
    }
}
