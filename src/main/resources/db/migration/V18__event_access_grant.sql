-- ============================================================================
-- V18 — Private-event access grants (EVENT_DOMAIN_SPEC §8)
--
-- A shareable bearer token that lets an invitee view and buy an otherwise-hidden
-- PRIVATE event. The organizer creates and revokes grants.
-- ============================================================================
CREATE TABLE event_access_grant (
    id         UUID PRIMARY KEY,
    event_id   UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    label      VARCHAR(255),
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_grant_event ON event_access_grant (event_id);
