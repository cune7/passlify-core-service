# Passlify — Rewrite Specification

Clean-room spec for rebuilding the Passlify ticketing platform (entrio.hr-style) as a
**Spring Boot 3 / Java 17** service. Reverse-engineered from the legacy NestJS service, then
simplified and corrected — the legacy bloat and bugs are intentionally left behind.

## How to use these docs
1. Read in this order: **SCOPE → DOMAIN → ARCHITECTURE → BUILD-SETUP**.
2. **Edit them first.** Cut features in SCOPE, adjust fields in DOMAIN — they're the contract.
3. Generate the skeleton (BUILD-SETUP §1), then build vertical slices in the order in SCOPE §"build order".
4. Hand any single doc to an AI/dev as a self-contained brief.

## The documents
| Doc | What it answers |
|-----|-----------------|
| [SCOPE.md](./SCOPE.md) | What the MVP builds, what's deferred, what's dropped, build order |
| [DOMAIN.md](./DOMAIN.md) | Entities, fields, relationships, enums, and the **business rules/flows** |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Stack, package layout, layering, conventions, testing |
| [BUILD-SETUP.md](./BUILD-SETUP.md) | Maven deps, `application.yml`, env vars, local infra, first-boot checklist |
| [DATABASE.md](./DATABASE.md) | Schema/migration notes (V1–V13) + entity↔table map |
| [API.md](./API.md) | REST endpoint contract: MVP core (§1–7) + Phase 1–3 additions (§8) |
| [FEATURES.md](./FEATURES.md) | **Live** feature/endpoint index by module + EVENT_DOMAIN_SPEC phase status |
| [EVENT_DOMAIN_SPEC.md](./EVENT_DOMAIN_SPEC.md) | Full Event-domain functional/technical spec (Phases 1–4) |
| [PAYMENTS.md](./PAYMENTS.md) | Providers, capabilities, gateway model + the Raiffeisen/UPC integration runbook |

## The core flow (the whole point)
> Organizer creates event + ticket types → buyer checks out (server-priced, inventory reserved
> atomically) → pays via Stripe → tickets issued with **signed** QR → operator scans at the door
> → ticket `VALID→USED`, double-entry blocked → refunds void tickets and release inventory.

## 5 correctness fixes carried over from the legacy analysis
These were **broken or missing** in the old system. They are hard requirements here:
1. **No oversell** — atomic inventory reservation (conditional UPDATE / optimistic lock). *(was: no locking)*
2. **Webhook idempotency** — `StripeEvent` ledger, insert-before-process. *(was: table existed but unused)*
3. **Signed QR tokens** — HMAC-SHA256, not plain JSON. *(was: forgeable plain payload)*
4. **Double-entry prevention** — scan locks the ticket and flips `VALID→USED`. *(was: status never updated)*
5. **Refund handling** — `charge.refunded` → void tickets + release inventory. *(was: not implemented)*

## Deliberately dropped vs legacy
Entitlement/AccessToken/UsageLog indirection, the 8 non-ticket product types, multi-provider
payments (PayPal/Coinbase), wallet/plans/offer/affiliate/reporting/consent modules. See SCOPE.md.
