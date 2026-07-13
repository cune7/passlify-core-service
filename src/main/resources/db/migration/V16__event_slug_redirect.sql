-- ============================================================================
-- V16 — Event slug redirects (EVENT_DOMAIN_SPEC §5.3)
--
-- When a published event's slug changes, the old slug is recorded here so old links
-- 301 to the current slug instead of 404ing.
-- ============================================================================
CREATE TABLE event_slug_redirect (
    id         UUID PRIMARY KEY,
    old_slug   VARCHAR(120) NOT NULL UNIQUE,
    event_id   UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_slug_redirect_event ON event_slug_redirect (event_id);
