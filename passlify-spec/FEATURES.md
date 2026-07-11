# Passlify Core — Feature List

> Current state of the service, module by module. Generated 2026-07-11 from the code
> (controllers, modules, migrations). Legend:
> ✅ implemented & wired · 🟡 implemented but on a stub/mock · ⏳ modelled, not built.

The MVP spine is complete end-to-end: **organizer creates event + ticket types →
buyer checks out → payment → tickets issued with signed QR → operator scans at the gate →
ticket marked used, double-entry blocked.** Beyond the original MVP scope, the project also has
custom attendee-form fields, an organizer dashboard, and an organization/company module.

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

## Events  `com.passlify.core.event`
- ✅ CRUD (organizer-scoped): `POST /api/v1/events`, `GET /{id}`, `GET` (list), `PATCH /{id}`.
- ✅ Lifecycle: `POST /{id}/publish`, `/unpublish`, `/cancel`. `EventStatus`: DRAFT · PUBLISHED · CANCELLED · COMPLETED.
- ✅ Paid events gated on a complete `COMPANY` organization (see `organization-domain-model`).
- ✅ Public read API: `GET /api/v1/public/events` (list) + `GET /api/v1/public/events/{slug}` — only PUBLISHED + PUBLIC.

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
- ✅ Flyway migrations V1–V4 (baseline, event-type seed, custom fields + attendees, organization).
- ✅ Global RFC-7807 error handling (`common/error`), Jakarta Bean Validation, security config.
- ✅ OpenAPI/Swagger (`OpenApiConfig`). Actuator health.
- ✅ Validation isolated in per-module `*Validator` services. See memory `validator-architecture`.

---

## Notable gaps / next candidates
1. 🟡 **Real Stripe integration** — replace `MockPaymentGateway` with a live Stripe Checkout + webhook implementation (biggest open item vs. MVP scope).
2. ⏳ PIB check-digit validation (needs real known-good PIBs first — see `organization-domain-model`).
3. ⏳ Still deferred by design: coupons/discounts, tax/VAT, multi-day/season passes, seat selection, ticket transfer/resale, organizer payouts (Stripe Connect), additional payment providers. Schema hooks exist for most (see `SCOPE.md`).
