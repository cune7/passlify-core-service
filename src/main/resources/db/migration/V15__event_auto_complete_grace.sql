-- ============================================================================
-- V15 — Per-event auto-completion grace override (EVENT_DOMAIN_SPEC §7.4)
--
-- A published event whose end time (+ grace) has passed is auto-completed by a
-- scheduled sweep. Grace defaults to a platform setting; an admin may override it
-- per event here. NULL = use the platform default.
-- ============================================================================
ALTER TABLE event ADD COLUMN auto_complete_grace_hours INT;
