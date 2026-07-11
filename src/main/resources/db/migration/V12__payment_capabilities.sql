-- ============================================================================
-- V12 — Organizer payment capabilities (EVENT_DOMAIN_SPEC §10)
--
-- Admin-granted approval for an organization to use a payment provider, with the
-- currencies it may charge in. A paid event may only publish on a provider for which
-- its organization holds a usable (ACTIVE, in-window) capability.
-- ============================================================================
CREATE TABLE organizer_payment_capability (
    id                               UUID PRIMARY KEY,
    organization_id                  UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    provider                         VARCHAR(20) NOT NULL,
    status                           VARCHAR(20) NOT NULL,
    allowed_currencies               TEXT[],
    merchant_configuration_reference VARCHAR(255),
    valid_from                       TIMESTAMPTZ,
    valid_until                      TIMESTAMPTZ,
    approved_by                      VARCHAR(64),
    approved_at                      TIMESTAMPTZ,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_capability_provider CHECK (provider IN ('NONE','MOCK','MANUAL','RAIFFEISEN','STRIPE')),
    CONSTRAINT ck_capability_status   CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','REVOKED','EXPIRED')),
    CONSTRAINT uq_capability_org_provider UNIQUE (organization_id, provider)
);

CREATE INDEX idx_capability_org ON organizer_payment_capability (organization_id);
