-- ============================================================================
-- V14 — Organization bank details (for MANUAL / bank-transfer payments, §10)
--
-- Where a buyer sends an offline transfer for a MANUAL-provider event; rendered on
-- the manual payment-instructions page. Nullable — only needed to accept MANUAL.
-- ============================================================================
ALTER TABLE organization ADD COLUMN bank_account_number VARCHAR(64);
ALTER TABLE organization ADD COLUMN bank_account_holder VARCHAR(255);
