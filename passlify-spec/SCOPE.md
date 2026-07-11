
# Passlify — MVP Scope

> What the first clean version builds, and what it deliberately does **not**. This is your
> lever for controlling effort — edit freely before the build starts.

## Goal of the MVP
A working entrio.hr-style flow, end to end, correct and testable:

> **Organizer creates an event with ticket types → buyer checks out and pays with Stripe →
> tickets are issued with signed QR codes → gate operator scans QR at the door → ticket marked
> used, double-entry blocked.**

Everything else is deferred until that spine is solid.

---

## ✅ In scope (MVP)

### Auth (Keycloak)
- Resource-server JWT validation against the existing `passlify` realm.
- Roles: `ORGANIZER` (manage own events), `OPERATOR` (scan), `ADMIN` (all), buyer can be a
  guest (email only) or authenticated.

### Events & ticket types
- CRUD events (organizer scope), publish (one-way), cancel.
- CRUD ticket types within an event (price, quantity, sale window, per-order cap).
- Public listing + public event detail (only `PUBLISHED` + `PUBLIC`).

### Checkout & payments
- Create order from `{ticketTypeId, quantity}[]` + buyer email (server-authoritative pricing).
- **Atomic inventory reservation** (no oversell).
- Stripe Checkout Session creation; redirect-based payment.
- Webhook handling with **idempotency ledger**: `checkout.session.completed`,
  `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`.
- Order state machine (PENDING_PAYMENT → PAID/FAILED → REFUNDED).

### Ticket issuance & delivery
- Issue one `Ticket` per quantity unit on payment success (idempotent).
- **Signed QR token** (HMAC-SHA256), QR image rendered on demand.
- Email delivery of tickets (links + PDF) — basic template.
- Endpoint to fetch a buyer's tickets / render a ticket PDF.

### Entry validation / scanning
- `POST /scan` for operators: verify signature → lock ticket → `VALID→USED` → audit `TicketScan`.
- Reject double-scan, wrong-event, void, bad-signature with clear reasons.
- Per-event scan summary (counts of issued / used / void).

### Refunds (handle, not initiate)
- React to Stripe `charge.refunded`: mark order/payment refunded, VOID tickets, release inventory.
- (Initiating refunds from an admin UI is deferred; webhook handling is in.)

### Cross-cutting
- Flyway migrations (versioned schema).
- Global error handling (RFC-7807 `application/problem+json`).
- Request validation (Jakarta Bean Validation).
- OpenAPI/Swagger docs.
- Health/readiness via Actuator.
- Seed data for `EventType`.

---

## ⏳ Deferred (design for it, don't build it)
Model the schema so these slot in later without migration pain, but **do not implement**:

| Feature | Why deferred | Hook left in model |
|---------|--------------|--------------------|
| Multi-day / season / membership passes | adds usage-limit + per-day-entry complexity | `TicketKind` enum, `TicketScan` log already supports counting |
| Coupons / discounts | not core to first sale | `Order.discountMinor` field exists |
| Tax / VAT | jurisdiction-specific | `Order.taxMinor` field exists |
| Multiple payment providers (PayPal, Coinbase) | Stripe proves the flow | `PaymentProvider` enum, generic `Payment` |
| Per-ticket custom fields (attendee forms) | use `meta` jsonb for now | `OrderItem.meta`, `Ticket.meta` |
| Seat selection / reserved seating | big subsystem | — |
| Cart (multi-event basket) | checkout takes items directly | order/orderItem already multi-line |
| Affiliate / referral tracking | growth feature | `Order.meta.campaign` |
| Reporting / analytics dashboards | post-launch | scans + orders are queryable |
| Refund initiation UI | webhook handling covers correctness | refund handler exists |
| Ticket transfer / resale | secondary market | `Ticket.ownerCustomerId` |
| Organizer payouts / Stripe Connect | money movement to sellers | out of scope |

---

## ❌ Out of scope (not even modelled)
Everything from the legacy system not listed above: `wallet`, `plans`, `offer`,
`entitlement`/`accessToken`/`usageLog`, `consent` document stack, `seller` audit logging,
`digital-pass`/`course`/`donation`/`gift-card`/`code`/`product-pickup` product types,
i18n consent emails, Keycloak theme work.

---

## Suggested build order (vertical slices)
Build thin end-to-end slices so you always have something runnable:

1. **Skeleton + auth + health** — project boots, JWT validates, `/actuator/health` green.
2. **Events + ticket types CRUD** — organizer can model an event. Flyway V1.
3. **Public listing** — read-only buyer view.
4. **Checkout (no payment)** — create order, reserve inventory, compute totals. Tests for oversell.
5. **Stripe payment** — session creation + webhook + idempotency. Order → PAID.
6. **Ticket issuance + QR** — tickets created on PAID, signed QR, fetch/PDF.
7. **Scanning** — `/scan`, VALID→USED, double-entry tests.
8. **Refunds** — `charge.refunded` → VOID + release inventory.
9. **Email delivery** — send tickets after issuance.

Each slice = a PR with tests. Don't move on until the previous slice's correctness tests pass
(especially #4 oversell, #5 idempotency, #7 double-scan).