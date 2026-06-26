# Passlify — Domain Model

> Source of truth for the entities, fields, relationships and business rules of the
> Passlify ticketing platform (entrio.hr-style). Built for a clean Spring Boot 3 / Java 17
> rewrite. This document was reverse-engineered from the legacy NestJS service and then
> **simplified and corrected** — it is not a 1:1 copy.

## 0. Modelling principles

1. **Money is always an integer in minor units** (`*_minor`, e.g. cents). Never use floats.
   Base currency is **EUR**. A single currency per order.
2. **UUID (v7 preferred) primary keys** for every aggregate root. Avoids id enumeration and
   keeps types consistent. (Legacy mixed `int` and `uuid` — don't.)
3. **The Ticket is the unit of access.** There is no separate Entitlement/AccessToken/UsageLog
   layer (the legacy system had one; it was over-engineered). A ticket is issued, carries a
   **signed** QR, and moves `VALID → USED` on scan.
4. **All timestamps are `timestamptz` (UTC)**. Convert at the edge.
5. **State transitions are explicit and guarded** — never flip a status without checking the
   current one inside a transaction.

---

## 1. Entity overview

```
EventType ─┐
Location  ─┤
           └──< Event >──< TicketType >──┐
                  │                       │
                  │                       │
   Order >──< OrderItem >─────────────────┘
     │            │
     │            └──< Ticket >──< TicketScan
     └──< Payment
StripeEvent  (standalone — webhook idempotency ledger)
```

`A >──< B` = one-to-many (A is the "one"). `──<` arrow points to the "many".

---

## 2. Entities

### 2.1 `EventType`  (lookup table)
Classification of an event. Seeded reference data.

| field      | type            | notes                                   |
|------------|-----------------|-----------------------------------------|
| id         | UUID PK         |                                         |
| category   | varchar(80)     | e.g. "Music", "Sport", "Conference"     |
| type       | varchar(120)    | e.g. "Concert", "Festival"              |
| createdAt  | timestamptz     |                                         |

**Constraint:** unique `(category, type)`.

### 2.2 `Location`
A physical venue. May be reused across events.

| field      | type          | notes                |
|------------|---------------|----------------------|
| id         | UUID PK       |                      |
| venueName  | varchar(255)  | not null             |
| address    | varchar(255)  | not null             |
| city       | varchar(120)  | not null             |
| country    | varchar(2)    | ISO-3166-1 alpha-2   |
| postalCode | varchar(20)   | not null             |

**Constraint:** unique `(venueName, address, city, country, postalCode)`.

### 2.3 `Event`
The thing people buy tickets for.

| field         | type           | notes                                                        |
|---------------|----------------|--------------------------------------------------------------|
| id            | UUID PK        |                                                              |
| slug          | citext unique  | human URL, e.g. `summer-fest-2026`. Generated from name + suffix |
| name          | varchar(255)   | not null                                                     |
| description   | text           | nullable                                                     |
| coverImageUrl | varchar(2048)  | nullable                                                     |
| status        | enum `EventStatus` | see §3.1, default `DRAFT`                                 |
| visibility    | enum `Visibility`  | `PUBLIC` / `PRIVATE`, default `PRIVATE`                   |
| startsAt      | timestamptz    | not null                                                     |
| endsAt        | timestamptz    | not null, must be `> startsAt`                               |
| eventTypeId   | UUID FK → EventType | nullable                                                |
| locationId    | UUID FK → Location  | nullable                                                |
| organizerId   | varchar(64)    | Keycloak `sub` of the seller/organizer. not null            |
| capacity      | int            | nullable (overall cap; ticket types also have their own)    |
| tags          | text[]         | nullable                                                     |
| createdAt / updatedAt | timestamptz |                                                       |

**Rules**
- Only `status = PUBLISHED` + `visibility = PUBLIC` events appear in public listings.
- An event can only be `PUBLISHED` if it has ≥1 active `TicketType` and `startsAt` in the future.
- `slug` is immutable once `PUBLISHED`.

### 2.4 `TicketType`   (legacy name: `TicketVariant`)
A sellable category of ticket within an event ("VIP", "Regular", "Student").

| field          | type             | notes                                                   |
|----------------|------------------|---------------------------------------------------------|
| id             | UUID PK          |                                                         |
| eventId        | UUID FK → Event  | not null                                                |
| name           | varchar(120)     | "VIP", "Regular"…                                       |
| description    | text             | nullable                                                |
| priceMinor     | int              | price in cents, ≥ 0 (0 = free ticket)                   |
| currency       | char(3)          | always `EUR` for MVP                                    |
| totalQuantity  | int              | inventory cap, > 0                                      |
| soldQuantity   | int              | **denormalized counter**, default 0 (see §4.2)          |
| maxPerOrder    | int              | per-order purchase cap, default 10                      |
| salesStartAt   | timestamptz      | nullable — sales open                                   |
| salesEndAt     | timestamptz      | nullable — sales close                                  |
| isActive       | boolean          | default true; single source of truth for "sellable"     |
| kind           | enum `TicketKind`| default `SINGLE_USE` (see §3.4; others deferred)        |
| createdAt / updatedAt | timestamptz |                                                      |

**Derived:** `availableQuantity = totalQuantity - soldQuantity`.

**Rules**
- Sellable iff: `isActive` AND `now` within `[salesStartAt, salesEndAt]` (nulls = open) AND
  `availableQuantity >= requestedQty` AND parent event is `PUBLISHED`.
- `priceMinor` is authoritative — the **server** computes order totals, never trusts client prices.
- `currency` is forced to `EUR` on write.

### 2.5 `Order`
A purchase. One per checkout attempt.

| field            | type           | notes                                                   |
|------------------|----------------|---------------------------------------------------------|
| id               | UUID PK        |                                                         |
| status           | enum `OrderStatus` | see §3.2, default `PENDING_PAYMENT`                 |
| customerId       | varchar(64)    | Keycloak `sub`, nullable (guest checkout allowed)       |
| customerEmail    | varchar(320)   | not null                                                |
| customerName     | varchar(256)   | nullable                                                |
| currency         | char(3)        | `EUR`                                                   |
| subtotalMinor    | int            | sum of item totals                                      |
| discountMinor    | int            | default 0 (coupons deferred)                            |
| taxMinor         | int            | default 0 (tax deferred)                                |
| totalMinor       | int            | `subtotal - discount + tax`                             |
| provider         | varchar(50)    | `stripe` (only provider in MVP)                         |
| providerIntentId | varchar(255)   | Stripe PaymentIntent or Checkout Session id             |
| returnUrl        | varchar(1024)  | where to send the buyer after payment                   |
| paidAt           | timestamptz    | nullable, set on PAID                                    |
| meta             | jsonb          | nullable (language, campaign, etc.)                     |
| createdAt / updatedAt | timestamptz |                                                      |

**Indexes:** `customerId`, `status`, `createdAt`.

### 2.6 `OrderItem`
A line in an order. **MVP sells only tickets**, so this references a `TicketType` directly
(legacy used a generic `type`/`refId` polymorphism for 8 product kinds — dropped).

| field           | type                | notes                                          |
|-----------------|---------------------|------------------------------------------------|
| id              | UUID PK             |                                                |
| orderId         | UUID FK → Order     | not null, cascade delete                       |
| ticketTypeId    | UUID FK → TicketType| not null                                       |
| quantity        | int                 | 1–`maxPerOrder`                                |
| unitPriceMinor  | int                 | snapshot of price at purchase time             |
| totalPriceMinor | int                 | `unitPriceMinor * quantity`                    |
| meta            | jsonb               | nullable (attendee names, custom fields)       |

> Snapshot the price on the line item — if the organizer later edits `TicketType.priceMinor`,
> historical orders must not change.

### 2.7 `Payment`
A payment attempt against an order. One order may have multiple (retries).

| field                 | type           | notes                                    |
|-----------------------|----------------|------------------------------------------|
| id                    | UUID PK        |                                          |
| orderId               | UUID FK → Order| not null                                 |
| provider              | enum `PaymentProvider` | `STRIPE` (MVP)                   |
| status                | enum `PaymentStatus`   | see §3.3, default `PENDING`      |
| amountMinor           | int            | charged amount                           |
| currency              | char(3)        | `EUR`                                    |
| stripeSessionId       | varchar(255)   | nullable                                 |
| stripePaymentIntentId | varchar(255)   | nullable                                 |
| stripeChargeId        | varchar(255)   | nullable (needed for refunds)            |
| stripeCustomerId      | varchar(255)   | nullable                                 |
| refundedMinor         | int            | default 0 (supports partial refunds)     |
| metadata              | jsonb          | nullable                                 |
| createdAt / updatedAt | timestamptz    |                                          |

### 2.8 `StripeEvent`   (idempotency ledger — was UNUSED in legacy)
Every received webhook event is recorded **before** processing. This is the dedup guard.

| field        | type         | notes                                              |
|--------------|--------------|----------------------------------------------------|
| id           | varchar(255) PK | the Stripe event id (`evt_...`)                 |
| type         | varchar(120) | e.g. `checkout.session.completed`                  |
| payload      | jsonb        | raw event for audit/replay                         |
| receivedAt   | timestamptz  | not null                                           |
| processedAt  | timestamptz  | nullable; set when handling completes successfully |

**Rule:** insert `(id)` first; if it already exists → it's a duplicate → ACK 200 and skip. See §4.3.

### 2.9 `Ticket`
An issued, scannable ticket. Created on successful payment, one row per quantity unit.

| field           | type                | notes                                              |
|-----------------|---------------------|----------------------------------------------------|
| id              | UUID PK             |                                                    |
| orderId         | UUID FK → Order     | not null                                           |
| orderItemId     | UUID FK → OrderItem | not null                                           |
| ticketTypeId    | UUID FK → TicketType| not null                                           |
| eventId         | UUID FK → Event     | not null (denormalized for fast scan lookups)      |
| ownerCustomerId | varchar(64)         | Keycloak `sub`, nullable                           |
| ownerEmail      | varchar(320)        | who it was issued to                               |
| serialNumber    | varchar(64)         | human-readable, unique. Format §4.4                |
| qrToken         | varchar(512)        | **signed** token encoded into the QR. §4.5         |
| status          | enum `TicketStatus` | `VALID` / `USED` / `VOID`, default `VALID`         |
| attendeeName    | varchar(256)        | nullable                                           |
| issuedAt        | timestamptz         | not null                                            |
| usedAt          | timestamptz         | nullable, set on successful scan                   |
| scanCount       | int                 | default 0                                          |
| meta            | jsonb               | nullable (buyer/event snapshot for offline PDF)    |

**Indexes:** `eventId`, `orderId`, `ticketTypeId`, unique `serialNumber`, unique `qrToken`.

> The QR **image** is not stored — it is rendered on demand from `qrToken`. (Legacy stored a
> base64 PNG on the row, which bloats the table.)

### 2.10 `TicketScan`   (audit log — replaces legacy `UsageLog`)
One row per scan attempt. Enables fraud audit and (future) multi-entry passes.

| field      | type             | notes                                                       |
|------------|------------------|-------------------------------------------------------------|
| id         | UUID PK          |                                                             |
| ticketId   | UUID FK → Ticket | nullable (null when token didn't resolve)                   |
| eventId    | UUID FK → Event  | nullable                                                    |
| scannedAt  | timestamptz      | not null                                                     |
| result     | enum `ScanResult`| `ALLOWED` / `DENIED`                                        |
| reason     | varchar(40)      | nullable; e.g. `ALREADY_USED`, `VOID`, `BAD_SIGNATURE`, `WRONG_EVENT`, `NOT_FOUND` |
| scannedBy  | varchar(64)      | Keycloak `sub` of the gate operator                         |
| gate       | varchar(80)      | nullable, free-text gate/door id                            |

---

## 3. Enums

### 3.1 `EventStatus`
`DRAFT` → `PUBLISHED` → `COMPLETED`; or `CANCELLED` from any non-completed state.

### 3.2 `OrderStatus`   (unified — legacy had two conflicting versions)
`DRAFT`, `PENDING_PAYMENT`, `PAID`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED`, `PARTIALLY_REFUNDED`

### 3.3 `PaymentStatus`
`PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED`, `PARTIALLY_REFUNDED`

### 3.4 `TicketKind`
`SINGLE_USE` (MVP). Deferred: `MULTI_DAY_PASS`, `SEASON_PASS`, `MEMBERSHIP`.

### 3.5 `TicketStatus`
`VALID`, `USED`, `VOID`

### 3.6 `PaymentProvider`
`STRIPE` (MVP). Deferred: `PAYPAL`, `COINBASE`.

### 3.7 `Visibility` / `ScanResult`
`PUBLIC`/`PRIVATE`; `ALLOWED`/`DENIED`.

---

## 4. Business rules & flows  (the important part)

> These encode the *correctness* that the legacy system got wrong. Treat them as hard
> requirements, with tests.

### 4.1 Pricing (server-authoritative)
- Client sends only `{ticketTypeId, quantity}` pairs — **never prices**.
- Server loads each `TicketType`, validates it is sellable (§2.4), computes
  `unitPriceMinor` from the DB, and sums `totalMinor`.
- Reject if any ticket type is not sellable or `quantity > maxPerOrder`.

### 4.2 Inventory (LEGACY BUG: no locking → oversell)
On order creation, reserve inventory **atomically**:
- Within a DB transaction, for each line:
  `UPDATE ticket_type SET sold_quantity = sold_quantity + :qty
   WHERE id = :id AND sold_quantity + :qty <= total_quantity`
- If `rowsAffected = 0` → sold out → fail the whole order (roll back).
- This conditional-update pattern prevents oversell without explicit row locks.
- On order `FAILED`/`EXPIRED`/`CANCELLED` before payment → **release**: `sold_quantity -= qty`.
- On `REFUNDED` → release as well (tickets are VOIDed, §4.7).

> Alternative: optimistic locking via `@Version` on TicketType. The conditional UPDATE is
> simpler and equally safe — recommended.

### 4.3 Webhook idempotency (LEGACY BUG: StripeEvent table unused → double fulfillment)
1. Verify the Stripe signature using the raw request body + webhook secret. Reject 400 on fail.
2. `INSERT INTO stripe_event (id, type, payload, received_at)`.
   - If the insert violates the PK (duplicate `evt_...`) → **already processed/seen → ACK 200, stop.**
3. Process the event (§4.5 / §4.7) inside a transaction.
4. Set `processedAt = now`.
- Always return 200 quickly for events you don't handle (just record them).

### 4.4 Serial number format
`{EVENTPREFIX}-{ORDER6}-{NNN}` where:
- `EVENTPREFIX` = first 4 alphanumerics of event slug, uppercased
- `ORDER6` = first 6 hex chars of orderId (no dashes), uppercased
- `NNN` = zero-padded per-order ticket counter
- Must be globally unique (DB unique constraint is the guard).

### 4.5 Ticket issuance (on payment success)
Triggered by `checkout.session.completed` / `payment_intent.succeeded`:
1. Transition order `PENDING_PAYMENT → PAID`, set `paidAt`. (Guard: only if currently PENDING_PAYMENT.)
2. Set the related `Payment.status = SUCCEEDED`, capture `stripeChargeId`/`stripePaymentIntentId`.
3. For each `OrderItem`, create `quantity` × `Ticket` rows:
   - `serialNumber` per §4.4
   - `qrToken` = **signed** token (§4.6), `status = VALID`, `issuedAt = now`
   - snapshot buyer + event info into `meta` for offline PDF rendering
4. (Optional) enqueue email delivery with PDF/links.
- **Idempotent:** if tickets already exist for the order (re-delivered webhook that slipped past
  §4.3), do not create duplicates — check before insert.

### 4.6 QR token signing (LEGACY BUG: plain JSON → forgeable)
- Token payload: `{ tid: ticketId, ev: eventId, sn: serialNumber }`.
- Sign with **HMAC-SHA256** using a server secret (`PASSLIFY_QR_SECRET`), encode as
  `base64url(payload).base64url(signature)`. (A signed JWT is an acceptable alternative.)
- The QR image encodes this token string. Generated on demand (ZXing), never stored as a blob.
- A valid signature proves the ticket was issued by us; the DB lookup proves it's still usable.

### 4.7 Entry scanning / validation (LEGACY BUG: ticket never marked USED → double entry)
Endpoint: `POST /scan` with `{ qrToken, eventId, gate? }`, called by a gate operator (authn'd).
Inside a single transaction:
1. Verify the `qrToken` signature. On failure → record `TicketScan(DENIED, BAD_SIGNATURE)`, return denied.
2. Load the `Ticket` (lock the row: `SELECT … FOR UPDATE`).
   - Not found → `DENIED, NOT_FOUND`.
   - `ticket.eventId != eventId` → `DENIED, WRONG_EVENT`.
   - `status == VOID` → `DENIED, VOID`.
   - `status == USED` → `DENIED, ALREADY_USED` (return when it was first used).
3. If `VALID`: set `status = USED`, `usedAt = now`, `scanCount += 1`.
   Record `TicketScan(ALLOWED)`. Return allowed + attendee/event info.
- The `FOR UPDATE` lock makes concurrent scans of the same ticket safe (only the first wins).

### 4.8 Refunds (LEGACY BUG: not implemented at all)
Triggered by Stripe `charge.refunded`:
1. Find the `Payment` by `stripeChargeId`/`paymentIntentId`.
2. Increment `Payment.refundedMinor`; set `PaymentStatus` = `REFUNDED` (full) or `PARTIALLY_REFUNDED`.
3. Set `Order.status` = `REFUNDED` / `PARTIALLY_REFUNDED`.
4. On full refund: mark all the order's tickets `status = VOID` and **release inventory** (§4.2).
- Idempotent via §4.3.

---

## 5. What we deliberately dropped from the legacy system
- `Entitlement`, `AccessToken`, `UsageLog` indirection → folded into `Ticket` + `TicketScan`.
- Generic multi-product `OrderItem` (digital-pass, membership, course, donation, gift-card, code,
  pickup) → MVP sells **tickets only**.
- Modules: `affiliate`, `wallet`, `plans`, `offer`, `reporting`, `entitlement`, `seller` audit,
  `consent` stack, multi-provider payments (PayPal/Coinbase).
- Per-ticket custom `TicketField`/`TicketFieldValue` → use `OrderItem.meta` / `Ticket.meta` for now.
- See [SCOPE.md](./SCOPE.md) for the full keep/defer list.