-- ============================================================================
-- V6 — Event settings (EVENT_DOMAIN_SPEC §20)
--
-- Structured event conditions, one row per event (PK = FK to event), created
-- lazily when an organizer first configures them.
-- ============================================================================
CREATE TABLE event_settings (
    event_id                              UUID PRIMARY KEY REFERENCES event (id) ON DELETE CASCADE,
    minimum_age                           INT,
    tickets_available_at_entrance         BOOLEAN NOT NULL DEFAULT FALSE,
    visitor_country_restriction_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_visitor_country_codes         TEXT[],
    multiple_entry_allowed                BOOLEAN NOT NULL DEFAULT FALSE,
    people_with_disabilities_free_entry   BOOLEAN NOT NULL DEFAULT FALSE,
    children_free_entry_age               INT,
    terms_html                            TEXT,
    additional_rules_html                 TEXT,
    CONSTRAINT ck_event_settings_min_age CHECK (minimum_age IS NULL OR (minimum_age BETWEEN 0 AND 120)),
    CONSTRAINT ck_event_settings_child_age CHECK (children_free_entry_age IS NULL OR (children_free_entry_age BETWEEN 0 AND 18))
);
