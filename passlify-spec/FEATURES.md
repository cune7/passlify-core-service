# Passlify Core — Feature List

> Current state of the service, module by module. Updated through EVENT_DOMAIN_SPEC
> Phase 3 (migrations V1–V12). Legend:
> ✅ implemented & wired · 🟡 implemented but stubbed/unverified · ⏳ modelled, not built.

The MVP spine is complete end-to-end: **organizer creates event + ticket types →
buyer checks out → payment → tickets issued with signed QR → operator scans at the gate →
ticket marked used, double-entry blocked.** Beyond the original MVP scope, the project also has
custom attendee-form fields, an organizer dashboard, and an organization/company module.

---

## EVENT_DOMAIN_SPEC — phase status

- **Phase 1 — Event foundation ✅ complete.** Identity (immutable ULID `publicId` + slug),
  mandatory IANA timezone, explicit attendance & commercial mode, visibility incl. UNLISTED,
  sanitized rich-text description, event contact, settings (§20), online access (§14.5),
  immutable audit trail (§28), publication-readiness checklist (§23), lifecycle
  (publish one-way / cancel-with-reason / complete), EventType hierarchy (§19). Migrations V5–V9.
- **Phase 2 — Collaboration ✅ complete.** Event collaborators + roles (§13), invite→accept,
  ownership transfer (§13.4), full authorization matrix (§13.2), signed & expiring invitation
  tokens (§38). Migrations V10–V11.
- **Phase 3 — Commercial control 🟡 in progress.** Payment capabilities (§10) ✅ done;
  Raiffeisen gateway 🟡 config-gated + unit-tested but unverified against the bank; real Stripe
  SDK ⏳ pending. Migration V12.
- **Phase 4 — Advanced lifecycle ⏳ not started.** Slug redirects, automated completion,
  attendee schedule-change notifications, private-event invitations, event archival.

Test coverage: 68 tests (unit + Testcontainers-Postgres integration), all green.

---

## Auth & identity
- ✅ Resource-server JWT validation against the Keycloak `passlify` realm.
- ✅ Roles: `ORGANIZER` (manage own events), `OPERATOR` (scan), `ADMIN` (all); buyers can be guest (email only).
- Identity lives entirely in Keycloak — no user table. See memory `keycloak-identity-setup`.

## Organizations (company / billing)  `com.passlify.core.organization`
- ✅ `GET/PUT /api/v1/me/organization` — read/update the caller's organization (1:1 with user).
- ✅ `GET /api/v1/admin/organizations` — admin listing.
- ✅ `OrganizationKind`: `INDIVIDUAL` (auto-created, enough for free events) vs `COMPANY` (legal fields required to sell).
- ✅ Serbia tax-id validation (`VatNumbers`): PIB 9-digit, MBR 8-digit. PIB check-digit intentionally deferred.
- Company/billing data is in Postgres, not Keycloak. See memory `organization-domain-model`.

## Events  `com.passlify.core.event`  *(EVENT_DOMAIN_SPEC Phases 1–2 built)*
- ✅ CRUD (organizer-scoped): `POST /api/v1/events`, `GET /{id}`, `GET` (list), `PATCH /{id}` (optimistic `version` → 409 on stale edit).
- ✅ Identity: immutable ULID `publicId` + human `slug` (DRAFT-only slug edits). Mandatory IANA `timezone`.
- ✅ Explicit `AttendanceMode` (IN_PERSON/ONLINE/HYBRID) and `CommercialMode` (FREE/PAID); `Visibility` PUBLIC/UNLISTED/PRIVATE.
- ✅ Sanitized rich-text `descriptionHtml` (+ plain-text projection); embedded event contact/social links.
- ✅ Lifecycle: `POST /{id}/publish` (one-way — no unpublish), `/cancel` (reason required), `/complete`. `EventStatus`: DRAFT · PUBLISHED · CANCELLED · COMPLETED.
- ✅ `GET /{id}/publication-readiness` — structured violation checklist (name, type, location/online per mode, contact, tickets, paid company+provider, capacity).
- ✅ `GET/PUT /{id}/settings` — age, entry, country restriction (hard-reject when empty), rules (§20).
- ✅ `GET/PUT /{id}/online-access` — join URL/instructions for ONLINE/HYBRID (§14.5).
- ✅ Immutable audit trail (`EventAuditEntry`, JSON diff) + `GET /{id}/audit`; domain events published for cross-module reactions.
- ✅ Paid events gated on a complete `COMPANY` organization (see `organization-domain-model`).
- ✅ Public read API: `GET /api/v1/public/events` (list, PUBLIC only) + `GET /api/v1/public/events/{slug}` (PUBLIC + UNLISTED; PRIVATE 404s).
- ✅ `EventType` hierarchy (§19): non-selectable category parents + selectable leaves (`code`/`parent`/`active`/`sortOrder`); create enforces a selectable leaf; `GET /api/v1/public/event-types` catalog. Migration V9.
- ✅ Collaborators (§13): `EventCollaborator` (event-scoped roles OWNER/MANAGER/EDITOR/VIEWER/CHECK_IN_OPERATOR); creator stored as ACCEPTED OWNER; invite by email → accept links Keycloak sub; `GET/POST/PATCH/DELETE /events/{id}/collaborators` + `/accept`; audited + email notification. Migration V10.
- ✅ Ownership transfer (§13.4): `POST /events/{id}/transfer-ownership` (owner/admin, explicit confirm) → target becomes OWNER, previous owner becomes MANAGER, `organizerId` moves; audited. (Org reassignment for paid events deferred.)
- ✅ Authorization matrix (§13.2): `EventAuthorization` + `EventCapability` enforce role→capability across the event surface — VIEW / EDIT_DETAILS / EDIT_COMMERCIAL / MANAGE_LIFECYCLE / CONFIGURE_TICKETS / VIEW_REPORTS / MANAGE_COLLABORATORS / TRANSFER_OWNERSHIP. Applied in event edit/lifecycle, ticket config, dashboard, settings/online-access. Non-participants get 404, insufficient role 403.
- ✅ Signed invitation tokens (§38): `InvitationTokenService` (HMAC-SHA256) issues an expiring token on invite (14-day TTL); `POST /collaborators/accept` verifies signature + expiry + email match; lapsed invites flip to EXPIRED. Migration V11.

## Ticket types  `com.passlify.core.ticket`
- ✅ CRUD within an event: `POST/GET /api/v1/events/{eventId}/ticket-types`, `PATCH/DELETE /api/v1/ticket-types/{id}`.
- ✅ Price, quantity, sale window, per-order cap. `TicketKind` enum present (single vs future multi-day/pass kinds).

## Custom fields / attendee forms  `com.passlify.core.forms`
- ✅ Per-event custom fields: `POST/GET /api/v1/events/{eventId}/custom-fields`, `PATCH/DELETE /api/v1/custom-fields/{id}`.
  *(Was "deferred" in the original MVP scope — now built. Migration V3.)*

## Orders & checkout  `com.passlify.core.order`
- ✅ `POST /api/v1/orders` — create order from `{ticketTypeId, quantity}[]` + buyer email, server-authoritative pricing.
- ✅ Atomic inventory reservation (no oversell). `GET /api/v1/orders/{id}`.
- ✅ Order state machine. `OrderStatus`: DRAFT · PENDING_PAYMENT · PAID · FAILED · EXPIRED · CANCELLED · REFUNDED · PARTIALLY_REFUNDED.
- Fields modelled for later: `discountMinor`, `taxMinor`, `meta` (campaign).

## Payments  `com.passlify.core.payment`
- 🟡 **Payment gateway is a `MockPaymentGateway`** — real Stripe SDK is NOT wired in yet. This is the main gap vs. the MVP scope.
- ✅ Gateway abstraction: `PaymentGateway` + `PaymentGatewayRegistry` + `PaymentProvider` enum (ready for Stripe/others to slot in).
- ✅ `POST /api/v1/orders/{id}/payment-session` — create a checkout session (currently via mock).
- ✅ `POST /api/v1/webhooks/{provider}` — webhook handling with an **idempotency ledger** (`WebhookEvent` / `WebhookEventKey`).
- ✅ `PaymentStatus`: PENDING · SUCCEEDED · FAILED · REFUNDED · PARTIALLY_REFUNDED.
- ✅ Refund handling (react to refund event → mark refunded, VOID tickets, release inventory).
- ✅ **Payment capabilities (§10, Phase 3):** `OrganizerPaymentCapability` — admins grant/suspend/revoke which providers an org may use, with allowed currencies (`POST/GET /api/v1/admin/organizations/{id}/payment-capabilities`, `PATCH /api/v1/admin/payment-capabilities/{id}`, `GET /api/v1/me/payment-capabilities`). Real processors (STRIPE/RAIFFEISEN) require a usable capability covering the event currency to publish; NONE/MOCK/MANUAL are exempt. Migration V12.
- 🟡 **Raiffeisen gateway (UPC e-Commerce Connect Gateway):** `RaiffeisenPaymentGateway` + `UpcSignature` (RSA-SHA1 over the ordered request/response datafiles, per the merchant integration doc in `passlify-spec/docs/rfz-ecommerce-integration-docs`) — createSession builds the signed redirect fields; verifyAndParse verifies the NOTIFY signature with the gateway key and maps TranCode `000`→PAID. Unit-tested with generated RSA keypairs. **Config-gated** (`passlify.raiffeisen.enabled`, inert until merchant creds). Still to wire for a live flow: the POST-form redirect page and the NOTIFY_URL `Response.action=approve` handshake, then verify against the UPC test server. Tokenization (saved cards) not implemented.
- 🟡 **Stripe gateway** — still `MockPaymentGateway`; real Stripe SDK deferred (Raiffeisen chosen first).

## Ticket issuance & delivery  `com.passlify.core.issuance`
- ✅ One `Ticket` issued per quantity unit on payment success (idempotent). `TicketStatus`: VALID · USED · VOID.
- ✅ **Signed QR token** (`QrTokenService`, HMAC-SHA256); QR image + PDF rendered on demand (`qr/`, `pdf/`).
- ✅ Endpoints: `GET /api/v1/orders/{orderId}/tickets`, `GET /api/v1/tickets/{id}`, `.../qr`, `.../pdf`, `GET /api/v1/me/tickets`.

## Notifications  `com.passlify.core.notification`
- ✅ Email delivery of tickets after issuance — `EmailService` + `TicketIssuedListener` on a `TicketsIssuedEvent`.
- Dev SMTP goes to Mailpit (see `keycloak-identity-setup` / compose).

## Entry validation / scanning  `com.passlify.core.scan`
- ✅ `POST /api/v1/scan` (operators): verify signature → lock ticket → VALID→USED → audit.
- ✅ Rejects with clear reasons — `ScanDenyReason`: ALREADY_USED · VOID · WRONG_EVENT · BAD_SIGNATURE · NOT_FOUND.
- ✅ `GET /api/v1/events/{id}/scan-summary` — per-event counts.

## Dashboard / reporting  `com.passlify.core.dashboard`
- ✅ `GET /api/v1/events/{eventId}/orders`, `/attendees`, `/sales-summary`, `/attendees/export` (CSV).
  *(Reporting was "deferred" in scope — a basic organizer dashboard now exists.)*

## Cross-cutting  `com.passlify.core.common`, `config`
- ✅ Flyway migrations V1–V12 (baseline → event-type seed → custom fields/attendees → organization → event foundation → settings → online access → audit → event-type hierarchy → collaborators → invite expiry → payment capabilities).
- ✅ Global RFC-7807 error handling (`common/error`), Jakarta Bean Validation, security config.
- ✅ OpenAPI/Swagger (`OpenApiConfig`). Actuator health.
- ✅ Validation isolated in per-module `*Validator` services. See memory `validator-architecture`.

---

## Notable gaps / next candidates
1. 🟡 **Raiffeisen go-live** — confirm NestPay field set / hash version / result codes against the merchant integration doc + provision test-env store key, then verify redirect + callback end-to-end (task open).
2. 🟡 **Real Stripe integration** — replace `MockPaymentGateway` with a live Stripe Checkout + webhook implementation (needs Stripe test-mode keys; deferred behind Raiffeisen).
3. ⏳ **Event Phase 4** — slug redirects, automated completion, schedule-change notifications, private-event invitations, archival.
4. ⏳ Publish-time enforcement of contact/location (currently advisory in readiness; pending contact-editing DTOs). Exact §19.1 category catalog is a reference-data follow-up.
5. ⏳ PIB check-digit validation (needs real known-good PIBs first — see `organization-domain-model`).
6. ⏳ Still deferred by design: coupons/discounts, tax/VAT, multi-day/season passes, seat selection, ticket transfer/resale, organizer payouts (Stripe Connect). Schema hooks exist for most (see `SCOPE.md`).
