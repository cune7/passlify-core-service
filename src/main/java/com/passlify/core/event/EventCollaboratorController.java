package com.passlify.core.event;

import com.passlify.core.event.dto.AcceptInvitationRequest;
import com.passlify.core.event.dto.CollaboratorResponse;
import com.passlify.core.event.dto.InviteCollaboratorRequest;
import com.passlify.core.event.dto.UpdateCollaboratorRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event collaborators: invite, list, re-role, remove, and accept an invitation. */
@RestController
@RequestMapping("/api/v1/events/{eventId}/collaborators")
@PreAuthorize("isAuthenticated()")
public class EventCollaboratorController {

    private final EventCollaboratorService collaborators;

    public EventCollaboratorController(EventCollaboratorService collaborators) {
        this.collaborators = collaborators;
    }

    @GetMapping
    public List<CollaboratorResponse> list(@PathVariable UUID eventId) {
        return collaborators.list(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollaboratorResponse invite(@PathVariable UUID eventId,
                                       @Valid @RequestBody InviteCollaboratorRequest req) {
        return collaborators.invite(eventId, req);
    }

    @PatchMapping("/{collaboratorId}")
    public CollaboratorResponse changeRole(@PathVariable UUID eventId,
                                           @PathVariable UUID collaboratorId,
                                           @Valid @RequestBody UpdateCollaboratorRoleRequest req) {
        return collaborators.changeRole(eventId, collaboratorId, req);
    }

    @DeleteMapping("/{collaboratorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID eventId, @PathVariable UUID collaboratorId) {
        collaborators.remove(eventId, collaboratorId);
    }

    @PostMapping("/accept")
    public CollaboratorResponse accept(@PathVariable UUID eventId,
                                       @Valid @RequestBody AcceptInvitationRequest req) {
        return collaborators.accept(eventId, req.token());
    }
}
