package com.passlify.core.event.dto;

import com.passlify.core.event.EventRole;
import jakarta.validation.constraints.NotNull;

/** Change an existing collaborator's event-scoped role. */
public record UpdateCollaboratorRoleRequest(@NotNull EventRole role) {
}
