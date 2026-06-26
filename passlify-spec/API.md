# Passlify — API Contract (MVP)

> REST contract for the MVP slices. Pairs with [DOMAIN.md](./DOMAIN.md) (entities/rules) and
> [SCOPE.md](./SCOPE.md) (build order). springdoc generates the live OpenAPI from your code — this
> is the human design target, not the generated artifact.

## Conventions
- Base path: **`/api/v1`**. JSON in/out. Plural nouns.
- All bodies are validated (Jakarta Bean Validation). Money is integer **minor units** (cents).
- Timestamps are ISO-8601 UTC (`2026-07-01T20:00:00Z`).
- Errors use RFC-7807 `application/problem+json` (see §Errors).
- Auth: Bearer JWT from Keycloak realm `passlify`. Roles map from `realm_access.roles`.
- **Ownership**: `ORGANIZER` endpoints additionally require `event.organizerId == jwt.sub`
  (an organizer can only touch their own events). `ADMIN` bypasses ownership.

### Roles per endpoint group
| Group | Auth |
|-------|------|
| Public catalog (`GET` events/detail) | none |
| Organizer event/ticket-type management | `ORGANIZER` or `ADMIN` (+ ownership) |
| Checkout (create order) | none (guest) or any authenticated user |
| Payment session | order owner or guest with order id |
| Stripe webhook | none — verified by Stripe signature |
| My tickets | authenticated buyer (or signed link for guests) |
| Scan | `OPERATOR` or `ADMIN` |

---

## 1. Events (organizer management)

### `POST /api/v1/events` — create event · `ORGANIZER`
Request:
```json
{
  "name": "Summer Fest 2026",
  "description": "Open-air festival",
  "startsAt": "2026-07-01T18:00:00Z",
  "endsAt": "2026-07-01T23:59:00Z",
  "eventTypeId": "…uuid|null",
  "location": {                          // inline create-or-reuse, or pass locationId
    "venueName": "Arena Zagreb", "address": "Lanište 1a",
    "city": "Zagreb", "country": "HR", "postalCode": "10000"
  },
  "capacity": 5000,
  "tags": ["festival", "summer"],
  "visibility": "PRIVATE"
}
```
Response `201`: full `EventResponse` (see §schemas). `slug` auto-generated, `status=DRAFT`.

### `GET /api/v1/events/{id}` — get own event (any status) · `ORGANIZER`
Response `200`: `EventResponse`.

### `PATCH /api/v1/events/{id}` — update · `ORGANIZER`
Partial body of the create fields. `slug` immutable once `PUBLISHED`. Response `200`.

### `POST /api/v1/events/{id}/publish` — publish · `ORGANIZER`
Guards (DOMAIN §2.3): ≥1 active ticket type, `startsAt` in future. Response `200` or `409` if guards fail.

### `POST /api/v1/events/{id}/unpublish` — back to DRAFT · `ORGANIZER`
Response `200`.

### `POST /api/v1/events/{id}/cancel` — cancel · `ORGANIZER`
Sets `status=CANCELLED`. Response `200`.

### `GET /api/v1/events` — list own events · `ORGANIZER`
Query: `status`, `page`, `size`, `sort`. Response `200`: `Page<EventSummary>`.

---

## 2. Ticket types (within an event)

### `POST /api/v1/events/{eventId}/ticket-types` — create · `ORGANIZER`
```json
{
  "name": "VIP",
  "description": "Front stage + lounge",
  "priceMinor": 12000,
  "totalQuantity": 200,
  "maxPerOrder": 6,
  "salesStartAt": "2026-05-01T00:00:00Z",
  "salesEndAt": "2026-06-30T23:59:00Z",
  "kind": "SINGLE_USE"
}
```
Response `201`: `TicketTypeResponse` (includes `availableQuantity = total - sold`).

### `GET /api/v1/events/{eventId}/ticket-types` — list · `ORGANIZER`
Response `200`: `TicketTypeResponse[]`.

### `PATCH /api/v1/ticket-types/{id}` — update · `ORGANIZER`
Partial. **Cannot** reduce `totalQuantity` below `soldQuantity` → `409`. Response `200`.

### `DELETE /api/v1/ticket-types/{id}` — delete · `ORGANIZER`
Only if `soldQuantity == 0` → else `409`. Response `204`.

---

## 3. Public catalog (no auth)

### `GET /api/v1/public/events` — browse published events
Query: `q` (text), `eventType`, `city`, `from`, `to`, `page`, `size`.
Only `status=PUBLISHED` + `visibility=PUBLIC`. Response `200`: `Page<PublicEventSummary>`.

### `GET /api/v1/public/events/{slug}` — public event detail
Response `200`: `PublicEventDetail` (event + sellable ticket types with `availableQuantity`,
`priceMinor`, sale window). `404` if not public/published.

---

## 4. Checkout (create order)

### `POST /api/v1/orders` — create order + reserve inventory
Guest-allowed. If authenticated, `customerId` taken from JWT.
```json
{
  "buyer": { "email": "ana@example.com", "name": "Ana K." },   // email required
  "items": [
    { "ticketTypeId": "…uuid", "quantity": 2 },
    { "ticketTypeId": "…uuid", "quantity": 1 }
  ],
  "returnUrl": "https://app.passlify.hr/checkout/return"
}
```
Server behavior (DOMAIN §4.1–4.2): prices from DB, validates sellable + `maxPerOrder`,
**atomically reserves inventory**. Response `201`:
```json
{
  "orderId": "…uuid",
  "status": "PENDING_PAYMENT",
  "currency": "EUR",
  "subtotalMinor": 31000,
  "totalMinor": 31000,
  "items": [ { "ticketTypeId": "…", "quantity": 2, "unitPriceMinor": 12000, "totalPriceMinor": 24000 } ],
  "expiresAt": "2026-06-13T12:15:00Z"            // optional reservation TTL
}
```
Errors: `409 SOLD_OUT` (oversell), `422` (ticket type not sellable / qty > maxPerOrder).

### `GET /api/v1/orders/{id}` — order status
Owner or guest-with-id. Response `200`: `OrderResponse` (status, items, payment summary).

---

## 5. Payment (Stripe)

### `POST /api/v1/orders/{id}/payment-session` — create Stripe Checkout Session
Body: optional `{ "successUrl": "...", "cancelUrl": "..." }` (defaults from `returnUrl`).
Server creates a Stripe Checkout Session with `metadata.orderId`, persists a `Payment`
(`status=PENDING`, `stripeSessionId`). Response `201`:
```json
{ "paymentId": "…uuid", "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_…", "sessionId": "cs_…" }
```
Client redirects the buyer to `checkoutUrl`. Only valid while order is `PENDING_PAYMENT` → else `409`.

### `POST /api/v1/webhooks/stripe` — Stripe webhook (no app auth)
- **Raw body required** for signature verification (ARCHITECTURE §4.7).
- Verify `Stripe-Signature` against `STRIPE_WEBHOOK_SECRET` → `400` on failure.
- Record event in `stripe_event` (dedup, DOMAIN §4.3); duplicates → `200` no-op.
- Handle: `checkout.session.completed`, `payment_intent.succeeded` → order `PAID` + issue tickets;
  `payment_intent.payment_failed` → order `FAILED` + release inventory;
  `charge.refunded` → refund flow (DOMAIN §4.8).
- Always respond **`200`** fast for received events.

---

## 6. Tickets (buyer)

### `GET /api/v1/me/tickets` — my tickets · authenticated
Query: `eventId?`, `page`, `size`. Response `200`: `Page<TicketResponse>`.

### `GET /api/v1/tickets/{id}` — single ticket · owner / signed link
Response `200`: `TicketResponse` (incl. `serialNumber`, `status`, event/attendee info).

### `GET /api/v1/tickets/{id}/qr` — QR image · owner / signed link
Response `200`: `image/png` (rendered on demand from `qrToken`).

### `GET /api/v1/tickets/{id}/pdf` — ticket PDF · owner / signed link
Response `200`: `application/pdf`.

### `GET /api/v1/orders/{id}/tickets` — all tickets for an order · owner / guest-with-id
Response `200`: `TicketResponse[]`.

---

## 7. Scanning (gate operators)

### `POST /api/v1/scan` — validate & redeem a ticket · `OPERATOR`
```json
{ "qrToken": "eyJ…sig", "eventId": "…uuid", "gate": "north-gate" }
```
Server (DOMAIN §4.7): verify signature → lock ticket row → check event/status → flip `VALID→USED`
→ record `TicketScan`. Always `200` with a verdict body (deny is not an HTTP error — it's a result):
```json
{
  "result": "ALLOWED",                  // or "DENIED"
  "reason": null,                        // or ALREADY_USED | VOID | WRONG_EVENT | BAD_SIGNATURE | NOT_FOUND
  "ticket": {
    "id": "…", "serialNumber": "SUMM-A1B2C3-001",
    "ticketTypeName": "VIP", "attendeeName": "Ana K.",
    "status": "USED", "usedAt": "2026-07-01T18:42:10Z",
    "firstUsedAt": "2026-07-01T18:42:10Z"   // present when ALREADY_USED
  }
}
```

### `GET /api/v1/events/{id}/scan-summary` — live counts · `OPERATOR`/`ORGANIZER`
Response `200`: `{ "issued": 412, "used": 380, "void": 5, "valid": 27 }`.

---

## Response schemas (shapes; map to Java records)

`EventResponse` / `EventSummary`
```
id, slug, name, description, coverImageUrl, status, visibility,
startsAt, endsAt, eventType{category,type}, location{...}, organizerId,
capacity, tags, createdAt, updatedAt
```
`PublicEventDetail`
```
id, slug, name, description, coverImageUrl, startsAt, endsAt,
eventType, location, ticketTypes:[ {id,name,description,priceMinor,currency,availableQuantity,maxPerOrder,salesStartAt,salesEndAt} ]
```
`TicketTypeResponse`
```
id, eventId, name, description, priceMinor, currency, totalQuantity,
soldQuantity, availableQuantity, maxPerOrder, salesStartAt, salesEndAt, isActive, kind
```
`OrderResponse`
```
id, status, customerEmail, customerName, currency,
subtotalMinor, discountMinor, taxMinor, totalMinor,
items:[ {ticketTypeId, ticketTypeName, quantity, unitPriceMinor, totalPriceMinor} ],
payment:{ status, provider }, paidAt, createdAt
```
`TicketResponse`
```
id, serialNumber, status, eventId, eventName, startsAt,
ticketTypeName, attendeeName, ownerEmail, issuedAt, usedAt,
qrUrl, pdfUrl
```

---

## Errors (RFC-7807 `application/problem+json`)
```json
{
  "type": "https://passlify.hr/errors/sold-out",
  "title": "Sold out",
  "status": 409,
  "detail": "Ticket type 'VIP' has only 1 ticket left.",
  "code": "SOLD_OUT",
  "instance": "/api/v1/orders"
}
```
| HTTP | When | `code` examples |
|------|------|-----------------|
| 400 | malformed request / bad webhook signature | `VALIDATION_ERROR`, `BAD_SIGNATURE` |
| 401 | missing/invalid JWT | `UNAUTHENTICATED` |
| 403 | role ok but not the owner | `FORBIDDEN` |
| 404 | not found / not public | `NOT_FOUND` |
| 409 | state conflict | `SOLD_OUT`, `INVALID_STATE`, `ALREADY_PUBLISHED` |
| 422 | semantically invalid | `NOT_SELLABLE`, `QTY_EXCEEDS_MAX` |

> Scan denials are **not** HTTP errors — `POST /scan` returns `200` with `result:"DENIED"`. This keeps
> the gate client simple (every scan is a 200 with a verdict).
