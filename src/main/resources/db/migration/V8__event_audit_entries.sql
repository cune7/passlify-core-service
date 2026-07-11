-- ============================================================================
-- V8 — Event audit trail (EVENT_DOMAIN_SPEC §28)
--
-- Immutable, append-only history of material event changes. Written in the same
-- transaction as the change. changed_fields is a JSON diff {field:{from,to}}.
-- ============================================================================
CREATE TABLE event_audit_entries (
    id               UUID PRIMARY KEY,
    event_id         UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    event_public_id  VARCHAR(26)  NOT NULL,
    actor_user_id    VARCHAR(64)  NOT NULL,
    action           VARCHAR(60)  NOT NULL,
    changed_fields   JSONB,
    reason           VARCHAR(1000),
    request_id       VARCHAR(100),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(1000),
    occurred_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_event_audit_event_time ON event_audit_entries (event_id, occurred_at DESC);
