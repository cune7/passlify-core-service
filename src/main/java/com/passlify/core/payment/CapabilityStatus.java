package com.passlify.core.payment;

/**
 * Lifecycle of an organization's approval to use a payment provider
 * (EVENT_DOMAIN_SPEC §10.2). Only {@code ACTIVE} (within its validity window) permits
 * publishing a paid event on that provider.
 */
public enum CapabilityStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED,
    EXPIRED
}
