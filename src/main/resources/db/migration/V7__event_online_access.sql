-- ============================================================================
-- V7 — Event online access (EVENT_DOMAIN_SPEC §14.5)
--
-- Join URL / instructions for ONLINE and HYBRID events. Sensitive: revealed only
-- to eligible attendees (never on the public event entity). One row per event.
-- ============================================================================
CREATE TABLE event_online_access (
    event_id                          UUID PRIMARY KEY REFERENCES event (id) ON DELETE CASCADE,
    public_url                        VARCHAR(2048),
    platform_name                     VARCHAR(120),
    access_instructions_html          TEXT,
    reveal_only_after_registration    BOOLEAN NOT NULL DEFAULT TRUE,
    reveal_at                         TIMESTAMPTZ
);
