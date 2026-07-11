-- ============================================================================
-- V11 — Collaborator invitation expiry (EVENT_DOMAIN_SPEC §13.3, §38)
--
-- Invitations now carry an expiry; acceptance uses a signed, expiring token.
-- Existing rows (e.g. auto-created OWNERs) get a far-future expiry so they are
-- unaffected.
-- ============================================================================
ALTER TABLE event_collaborators ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE event_collaborators SET expires_at = TIMESTAMPTZ '2999-01-01 00:00:00+00'
 WHERE expires_at IS NULL;

ALTER TABLE event_collaborators ALTER COLUMN expires_at SET NOT NULL;
