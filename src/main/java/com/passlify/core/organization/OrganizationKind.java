package com.passlify.core.organization;

/**
 * INDIVIDUAL — a private organizer; enough for FREE events. Auto-created on first
 * event from the Keycloak profile.
 * COMPANY — a registered business with legal/billing details; required to sell
 * PAID (B2B) events.
 */
public enum OrganizationKind {
    INDIVIDUAL,
    COMPANY
}
