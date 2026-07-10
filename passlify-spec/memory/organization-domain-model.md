---
name: organization-domain-model
description: Company/billing data lives in the Postgres organization table (not Keycloak); paid events require a complete COMPANY org
metadata:
  type: project
---

Business/legal/billing data lives in the app's Postgres `organization` table (1:1 with the owning user via `owner_id` = Keycloak `sub`), **NOT in Keycloak**. Keycloak stays a thin, replaceable identity layer holding only login identity; legal/financial data needs real migrations + audit for invoicing. See [[keycloak-identity-setup]].

**`OrganizationKind`:**
- `INDIVIDUAL` — a private organizer, auto-created on first use; enough to publish/run a **free** event (falls back to a display name from the user's name+email if empty).
- `COMPANY` — a registered business with legal fields (`legal_name`, `vat_pib`, `registration_no` = MBR/matični broj, address). Required to sell tickets.

**Business rule:** a **paid** event is strictly B2B — it cannot be published or open checkout unless linked to a complete `COMPANY` organization; otherwise it returns 4xx. Enforced in the app (event/checkout path via validators), not the DB.

**Serbia tax IDs** (validated in `VatNumbers` wired into `OrganizationService`): PIB = 9 digits (optional `RS` prefix stripped/normalized); MBR = 8 digits. The PIB ISO 7064 MOD 11,10 check-digit is intentionally NOT enforced yet (see the note in `VatNumbers.java`) — a wrong checksum would reject valid companies. To add it, first verify against 2-3 real known-good PIBs; don't enable it blind. See [[ask-when-unsure-dont-guess]].
