-- ============================================================================
-- V17 — Event archival (EVENT_DOMAIN_SPEC §39 Phase 4)
--
-- Archived events are kept (orders, tickets, reports, audit intact) but hidden from
-- the default listings: the organizer board shows non-archived by default (a filter
-- reveals archived for reporting/history), and the public catalog never lists them.
-- ============================================================================
ALTER TABLE event ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_event_organizer_archived ON event (organizer_id, archived);
