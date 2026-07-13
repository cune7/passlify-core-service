package com.passlify.core.event.dto;

import jakarta.validation.constraints.Size;

/** Create a private-event access grant. {@code label} is an optional note (e.g. invitee email). */
public record CreateAccessGrantRequest(@Size(max = 255) String label) {
}
