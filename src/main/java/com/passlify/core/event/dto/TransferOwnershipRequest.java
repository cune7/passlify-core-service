package com.passlify.core.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Transfer event ownership to an existing accepted collaborator (EVENT_DOMAIN_SPEC
 * §13.4). Requires explicit confirmation; the outgoing owner becomes a MANAGER.
 */
public record TransferOwnershipRequest(
        @NotNull UUID targetCollaboratorId,
        boolean confirm) {

    @AssertTrue(message = "Ownership transfer must be explicitly confirmed")
    public boolean isConfirmed() {
        return confirm;
    }
}
