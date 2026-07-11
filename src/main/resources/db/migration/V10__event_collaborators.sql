-- ============================================================================
-- V10 — Event collaborators (EVENT_DOMAIN_SPEC §13)
--
-- Multiple users may manage one event, with event-scoped roles. Invitations start
-- keyed on email (user_id null) and are linked to a Keycloak subject on acceptance.
-- The event creator is stored as an ACCEPTED OWNER.
-- ============================================================================
CREATE TABLE event_collaborators (
    id                UUID PRIMARY KEY,
    event_id          UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id           VARCHAR(64),
    email             VARCHAR(320) NOT NULL,
    role              VARCHAR(30)  NOT NULL,
    invitation_status VARCHAR(20)  NOT NULL,
    invited_by        VARCHAR(64)  NOT NULL,
    invited_at        TIMESTAMPTZ  NOT NULL,
    accepted_at       TIMESTAMPTZ,
    CONSTRAINT ck_collab_role   CHECK (role IN ('OWNER','MANAGER','EDITOR','VIEWER','CHECK_IN_OPERATOR')),
    CONSTRAINT ck_collab_status CHECK (invitation_status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED'))
);

-- One invitation per email per event (case-insensitive).
CREATE UNIQUE INDEX uq_collab_event_email ON event_collaborators (event_id, lower(email));
-- A Keycloak subject may hold at most one collaborator row per event.
CREATE UNIQUE INDEX uq_collab_event_user ON event_collaborators (event_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_collab_event ON event_collaborators (event_id);
CREATE INDEX idx_collab_user  ON event_collaborators (user_id);
