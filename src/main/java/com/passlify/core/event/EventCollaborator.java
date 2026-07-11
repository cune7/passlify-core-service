package com.passlify.core.event;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A user invited to help manage one specific event (EVENT_DOMAIN_SPEC §13). Access is
 * event-scoped. An invitation starts keyed on {@code email} with a null {@code userId};
 * the Keycloak {@code sub} is linked on acceptance. The creating organizer is stored as
 * an {@code OWNER} collaborator (already ACCEPTED) when the event is created.
 */
@Getter
@Setter
@Entity
@Table(name = "event_collaborators")
public class EventCollaborator extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Keycloak subject; null while the invitation is still PENDING. */
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false, length = 20)
    private InvitationStatus invitationStatus;

    @Column(name = "invited_by", nullable = false, length = 64)
    private String invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
