-- ----------------------------------------------------------------------------
-- Organization (organizer profile / billing entity)
--
-- Identity (login, name, email, phone, roles) lives in Keycloak. This table holds
-- the BUSINESS profile of an organizer: a public display name and — for paid,
-- B2B events — the company's legal/billing details (VAT/PIB, matični broj, address).
--
-- One organization per owner (Keycloak sub), 1:1. kind=INDIVIDUAL is auto-created
-- on first event and is enough for FREE events; a paid event requires kind=COMPANY
-- with the legal fields filled (enforced at publish in EventService).
-- ----------------------------------------------------------------------------
CREATE TABLE organization (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id         VARCHAR(64)  NOT NULL,                 -- Keycloak sub; one org per owner
    kind             VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    display_name     VARCHAR(255) NOT NULL,                 -- shown publicly as the organizer
    legal_name       VARCHAR(255),                          -- registered company name (COMPANY)
    vat_pib          VARCHAR(32),                           -- tax id / PIB
    registration_no  VARCHAR(32),                           -- matični broj (MBR)
    address_line     VARCHAR(512),
    city             VARCHAR(120),
    postal_code      VARCHAR(20),
    country          VARCHAR(2),                            -- ISO-3166 alpha-2
    contact_email    VARCHAR(320),                          -- billing/invoice contact
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_owner UNIQUE (owner_id),
    CONSTRAINT ck_org_kind  CHECK (kind IN ('INDIVIDUAL','COMPANY'))
);

CREATE INDEX idx_org_owner ON organization (owner_id);

-- Link events to the owning organization (nullable: legacy/free events may have none).
ALTER TABLE event ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE SET NULL;
CREATE INDEX idx_event_organization ON event (organization_id);
