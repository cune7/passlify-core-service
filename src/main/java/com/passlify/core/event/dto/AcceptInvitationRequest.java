package com.passlify.core.event.dto;

import jakarta.validation.constraints.NotBlank;

/** Accept a collaborator invitation using the signed token from the invite email. */
public record AcceptInvitationRequest(@NotBlank String token) {
}
