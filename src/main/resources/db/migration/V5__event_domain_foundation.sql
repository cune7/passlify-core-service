-- ============================================================================
-- V5 — Event domain foundation (EVENT_DOMAIN_SPEC Phase 1)
--
-- Extends the event aggregate with: an immutable public identifier (ULID),
-- explicit attendance & commercial modes, IANA timezone, sanitized rich-text
-- description (+ plain-text projection), event-specific contact/social links,
-- optimistic-lock version, and created_by/updated_by audit columns.
--
-- Enum widening: visibility += UNLISTED; payment_provider += NONE, RAIFFEISEN.
-- Backfill is safe on both empty (test) and populated databases.
-- ============================================================================

-- --- Rich text -------------------------------------------------------------
ALTER TABLE event RENAME COLUMN description TO description_html;
ALTER TABLE event ADD COLUMN description_plain_text TEXT;

-- --- New columns (nullable first, backfilled below, then constrained) ------
ALTER TABLE event ADD COLUMN public_id             VARCHAR(26);
ALTER TABLE event ADD COLUMN timezone              VARCHAR(64);
ALTER TABLE event ADD COLUMN attendance_mode       VARCHAR(20);
ALTER TABLE event ADD COLUMN commercial_mode       VARCHAR(20);
-- organization_id already added by V4__organization.sql
ALTER TABLE event ADD COLUMN version               BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE event ADD COLUMN created_by            VARCHAR(64);
ALTER TABLE event ADD COLUMN updated_by            VARCHAR(64);

-- Event-specific contact + social links (embedded EventContact)
ALTER TABLE event ADD COLUMN contact_email         VARCHAR(320);
ALTER TABLE event ADD COLUMN contact_phone         VARCHAR(40);
ALTER TABLE event ADD COLUMN website_url           VARCHAR(2048);
ALTER TABLE event ADD COLUMN facebook_url          VARCHAR(2048);
ALTER TABLE event ADD COLUMN instagram_url         VARCHAR(2048);
ALTER TABLE event ADD COLUMN youtube_url           VARCHAR(2048);
ALTER TABLE event ADD COLUMN linkedin_url          VARCHAR(2048);
ALTER TABLE event ADD COLUMN tiktok_url            VARCHAR(2048);
ALTER TABLE event ADD COLUMN x_url                 VARCHAR(2048);
ALTER TABLE event ADD COLUMN contact_show_email    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event ADD COLUMN contact_show_phone    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event ADD COLUMN contact_show_website  BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE event ADD COLUMN contact_show_social   BOOLEAN NOT NULL DEFAULT TRUE;

-- --- Backfill existing rows ------------------------------------------------
-- Public ID: a 26-char value for legacy rows; new rows get an app-generated ULID.
UPDATE event SET public_id = upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26))
 WHERE public_id IS NULL;

UPDATE event SET timezone = 'Europe/Belgrade' WHERE timezone IS NULL;
UPDATE event SET attendance_mode = 'IN_PERSON' WHERE attendance_mode IS NULL;

-- Commercial mode inferred from existing ticket prices (safe one-time inference).
UPDATE event e SET commercial_mode = CASE
        WHEN EXISTS (SELECT 1 FROM ticket_type t
                     WHERE t.event_id = e.id AND t.is_active AND t.price_minor > 0)
        THEN 'PAID' ELSE 'FREE' END
 WHERE commercial_mode IS NULL;

-- Free events do not process payments.
UPDATE event SET payment_provider = 'NONE' WHERE commercial_mode = 'FREE';

UPDATE event SET created_by = organizer_id WHERE created_by IS NULL;
UPDATE event SET updated_by = organizer_id WHERE updated_by IS NULL;

-- --- Enforce NOT NULL now that data is populated ---------------------------
ALTER TABLE event ALTER COLUMN public_id       SET NOT NULL;
ALTER TABLE event ALTER COLUMN slug            SET NOT NULL;
ALTER TABLE event ALTER COLUMN timezone        SET NOT NULL;
ALTER TABLE event ALTER COLUMN attendance_mode SET NOT NULL;
ALTER TABLE event ALTER COLUMN commercial_mode SET NOT NULL;
ALTER TABLE event ALTER COLUMN created_by      SET NOT NULL;
ALTER TABLE event ALTER COLUMN updated_by      SET NOT NULL;

-- --- Constraints & indexes -------------------------------------------------
ALTER TABLE event ADD CONSTRAINT uq_event_public_id UNIQUE (public_id);
ALTER TABLE event ADD CONSTRAINT ck_event_attendance_mode
    CHECK (attendance_mode IN ('IN_PERSON','ONLINE','HYBRID'));
ALTER TABLE event ADD CONSTRAINT ck_event_commercial_mode
    CHECK (commercial_mode IN ('FREE','PAID'));

-- Widen visibility and payment_provider enum check constraints.
ALTER TABLE event DROP CONSTRAINT ck_event_visibility;
ALTER TABLE event ADD  CONSTRAINT ck_event_visibility
    CHECK (visibility IN ('PUBLIC','PRIVATE','UNLISTED'));
ALTER TABLE event DROP CONSTRAINT ck_event_provider;
ALTER TABLE event ADD  CONSTRAINT ck_event_provider
    CHECK (payment_provider IN ('NONE','MOCK','MANUAL','RAIFFEISEN','STRIPE'));

ALTER TABLE payment DROP CONSTRAINT ck_payment_provider;
ALTER TABLE payment ADD  CONSTRAINT ck_payment_provider
    CHECK (provider IN ('NONE','MOCK','MANUAL','RAIFFEISEN','STRIPE'));

CREATE INDEX idx_event_public_listing ON event (status, visibility, starts_at);
