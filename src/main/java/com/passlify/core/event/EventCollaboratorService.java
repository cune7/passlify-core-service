package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.event.dto.CollaboratorResponse;
import com.passlify.core.event.dto.InviteCollaboratorRequest;
import com.passlify.core.event.dto.UpdateCollaboratorRoleRequest;
import com.passlify.core.notification.EmailService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages event collaborators and invitations (EVENT_DOMAIN_SPEC §13). Access is
 * event-scoped: the event owner, an ADMIN, or an accepted OWNER/MANAGER collaborator
 * may manage collaborators. The event creator is registered here as an ACCEPTED OWNER
 * when the event is created.
 */
@Service
public class EventCollaboratorService {

    private static final Set<EventRole> MANAGERIAL = Set.of(EventRole.OWNER, EventRole.MANAGER);

    private final EventRepository events;
    private final EventCollaboratorRepository collaborators;
    private final CurrentUser currentUser;
    private final EventAuditService audit;
    private final EmailService email;

    public EventCollaboratorService(EventRepository events,
                                    EventCollaboratorRepository collaborators,
                                    CurrentUser currentUser,
                                    EventAuditService audit,
                                    EmailService email) {
        this.events = events;
        this.collaborators = collaborators;
        this.currentUser = currentUser;
        this.audit = audit;
        this.email = email;
    }

    /** Registers the creating organizer as an ACCEPTED OWNER. Called from event creation. */
    public void createOwner(Event event) {
        String subject = event.getOrganizerId();
        EventCollaborator owner = new EventCollaborator();
        owner.setEventId(event.getId());
        owner.setUserId(subject);
        owner.setEmail(currentUser.email().orElse(subject));
        owner.setRole(EventRole.OWNER);
        owner.setInvitationStatus(InvitationStatus.ACCEPTED);
        owner.setInvitedBy(subject);
        owner.setInvitedAt(Instant.now());
        owner.setAcceptedAt(Instant.now());
        collaborators.save(owner);
    }

    @Transactional(readOnly = true)
    public List<CollaboratorResponse> list(UUID eventId) {
        Event event = loadEvent(eventId);
        requireCanManage(event);
        return collaborators.findByEventIdOrderByInvitedAtAsc(eventId).stream()
                .map(CollaboratorResponse::from)
                .toList();
    }

    @Transactional
    public CollaboratorResponse invite(UUID eventId, InviteCollaboratorRequest req) {
        Event event = loadEvent(eventId);
        requireCanManage(event);
        if (req.role() == EventRole.OWNER) {
            throw ApiException.validation("OWNER is assigned by ownership, not invitation");
        }
        collaborators.findByEventIdAndEmailIgnoreCase(eventId, req.email()).ifPresent(existing -> {
            throw ApiException.of(ErrorCode.CONFLICT, "That email is already a collaborator on this event");
        });

        EventCollaborator c = new EventCollaborator();
        c.setEventId(eventId);
        c.setEmail(req.email().trim());
        c.setRole(req.role());
        c.setInvitationStatus(InvitationStatus.PENDING);
        c.setInvitedBy(currentUser.requireSubject());
        c.setInvitedAt(Instant.now());
        EventCollaborator saved = collaborators.save(c);

        audit.record(event, EventAuditAction.COLLABORATOR_INVITED, null,
                "Invited " + req.email() + " as " + req.role());
        email.sendCollaboratorInvite(saved.getEmail(), event.getName(), req.role().name(),
                currentUser.displayName());
        return CollaboratorResponse.from(saved);
    }

    @Transactional
    public CollaboratorResponse changeRole(UUID eventId, UUID collaboratorId, UpdateCollaboratorRoleRequest req) {
        Event event = loadEvent(eventId);
        requireCanManage(event);
        if (req.role() == EventRole.OWNER) {
            throw ApiException.validation("Use transfer-ownership to assign OWNER");
        }
        EventCollaborator c = loadCollaborator(eventId, collaboratorId);
        if (c.getRole() == EventRole.OWNER) {
            throw ApiException.of(ErrorCode.CONFLICT, "The owner's role cannot be changed here");
        }
        c.setRole(req.role());
        audit.record(event, EventAuditAction.COLLABORATOR_ROLE_CHANGED, null,
                c.getEmail() + " -> " + req.role());
        return CollaboratorResponse.from(c);
    }

    @Transactional
    public void remove(UUID eventId, UUID collaboratorId) {
        Event event = loadEvent(eventId);
        requireCanManage(event);
        EventCollaborator c = loadCollaborator(eventId, collaboratorId);
        if (c.getRole() == EventRole.OWNER) {
            throw ApiException.of(ErrorCode.CONFLICT, "The event owner cannot be removed");
        }
        collaborators.delete(c);
        audit.record(event, EventAuditAction.COLLABORATOR_REMOVED, null, "Removed " + c.getEmail());
    }

    /** The invited user accepts, linking their Keycloak subject to the invitation. */
    @Transactional
    public CollaboratorResponse accept(UUID eventId, UUID collaboratorId) {
        Event event = loadEvent(eventId);
        EventCollaborator c = loadCollaborator(eventId, collaboratorId);
        if (c.getInvitationStatus() != InvitationStatus.PENDING) {
            throw ApiException.invalidState("This invitation is not pending");
        }
        String invitedEmail = c.getEmail();
        String callerEmail = currentUser.email().orElseThrow(() -> ApiException.of(
                ErrorCode.FORBIDDEN, "Your account has no email; cannot verify the invitation"));
        if (!callerEmail.equalsIgnoreCase(invitedEmail)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "This invitation was sent to a different email");
        }
        String subject = currentUser.requireSubject();
        collaborators.findByEventIdAndUserId(eventId, subject).ifPresent(existing -> {
            throw ApiException.of(ErrorCode.CONFLICT, "You already collaborate on this event");
        });
        c.setUserId(subject);
        c.setInvitationStatus(InvitationStatus.ACCEPTED);
        c.setAcceptedAt(Instant.now());
        audit.record(event, EventAuditAction.COLLABORATOR_ACCEPTED, null, invitedEmail + " accepted");
        return CollaboratorResponse.from(c);
    }

    // ---- helpers -----------------------------------------------------------

    private Event loadEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + eventId));
    }

    private EventCollaborator loadCollaborator(UUID eventId, UUID collaboratorId) {
        return collaborators.findByIdAndEventId(collaboratorId, eventId)
                .orElseThrow(() -> ApiException.notFound("Collaborator not found: " + collaboratorId));
    }

    /** Owner, ADMIN, or an accepted OWNER/MANAGER collaborator may manage collaborators. */
    private void requireCanManage(Event event) {
        if (currentUser.isAdmin()) {
            return;
        }
        String subject = currentUser.requireSubject();
        if (subject.equals(event.getOrganizerId())) {
            return;
        }
        boolean managerial = collaborators.findByEventIdAndUserId(event.getId(), subject)
                .filter(c -> c.getInvitationStatus() == InvitationStatus.ACCEPTED)
                .map(c -> MANAGERIAL.contains(c.getRole()))
                .orElse(false);
        if (!managerial) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "You may not manage collaborators for this event");
        }
    }
}
