package com.passlify.core.event.dto;

import com.passlify.core.event.EventRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Invite a collaborator by email with an event-scoped role (OWNER is not assignable). */
public record InviteCollaboratorRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull EventRole role) {
}
