-- ============================================================================
-- V13 — webhook_event.payload: jsonb → text
--
-- The raw provider payload is stored for audit/replay. Stripe/mock send JSON, but
-- the UPC/Raiffeisen gateway posts form-encoded bodies, which are not valid jsonb.
-- Store the payload as plain text so any provider's raw body can be recorded.
-- ============================================================================
ALTER TABLE webhook_event ALTER COLUMN payload TYPE text USING payload::text;
