# Passlify — Database

Flyway migrations for the schema in [DOMAIN.md](./DOMAIN.md). Drop these into your Spring Boot
project at `src/main/resources/db/migration/` and Flyway applies them on startup.

## Migrations
| File | What it does |
|------|--------------|
| `db/migration/V1__baseline.sql` | All 10 tables + constraints + indexes |
| `db/migration/V2__seed_event_types.sql` | Seeds `event_type` reference rows (idempotent) |

## Design choices (so they match the JPA entities)
- **snake_case columns.** Spring Boot's default `CamelCaseToUnderscoresNamingStrategy` maps entity
  fields like `priceMinor` → column `price_minor`. Keep entity fields camelCase; don't add `@Column`
  name overrides unless a column deviates.
- **Enums as `VARCHAR + CHECK`**, not Postgres native `ENUM` types. This lines up with
  `@Enumerated(EnumType.STRING)` and avoids the migration pain of altering native enums. Map each
  Java enum to the matching column; the `CHECK` lists the allowed values verbatim.
- **`gen_random_uuid()` default** (via `pgcrypto`) lets the DB fill the id, but your entities can
  still assign a UUID in `@PrePersist` — either works. If you prefer app-generated UUID v7, drop the
  DEFAULT and assign in code.
- **`ddl-auto: validate`** — Hibernate verifies entities match this schema at boot and fails fast on
  drift. Flyway is the single owner of schema changes.
- **Money** columns are `INT` minor units with `>= 0` CHECKs. `INT` max is ~21M EUR per amount —
  fine for ticketing. Switch to `BIGINT` if you ever need larger.
- **`orders`** (plural) because `order` is a reserved SQL keyword. Entity is `Order`,
  `@Table(name = "orders")`.
- **Timestamps** are `TIMESTAMPTZ` with `DEFAULT now()`. Set `hibernate.jdbc.time_zone: UTC`.

## Constraints that encode business rules
These back up the application logic (defense in depth — DON'T rely on them alone):
- `ck_tt_sold` — `sold_quantity <= total_quantity` makes oversell a DB error too, not just an app
  check. The atomic reservation UPDATE (DOMAIN §4.2) is still the primary guard.
- `ck_event_dates` — `ends_at > starts_at`.
- `ck_payment_amounts` — `refunded_minor <= amount_minor`.
- `uq_ticket_serial` / `uq_ticket_qr_token` — guarantee ticket identity/QR uniqueness.
- `stripe_event` PK on the Stripe event id — the idempotency guard (DOMAIN §4.3): a duplicate
  insert throws, which your handler treats as "already seen".

## Foreign-key delete behavior
- `order_item`, `payment` → `orders`: `ON DELETE CASCADE` (deleting an order cleans up its lines).
- `ticket_type` → `event`: `ON DELETE CASCADE`.
- `ticket` → `orders`/`order_item`/`ticket_type`/`event`: **no cascade** (issued tickets are records
  you should never silently lose; void them instead of deleting).
- `event` → `event_type`/`location`: `ON DELETE SET NULL` (lookups can change without orphaning events).
- `ticket_scan` → `ticket`/`event`: `ON DELETE SET NULL` (keep the audit row even if the ticket goes).

## Applying / testing
- **On app boot:** Flyway runs automatically (`spring.flyway.enabled=true`).
- **Manually with psql:**
  ```bash
  psql "postgresql://passlify:passlifypassword@localhost:5432/passlify_api" -f db/migration/V1__baseline.sql
  psql "postgresql://passlify:passlifypassword@localhost:5432/passlify_api" -f db/migration/V2__seed_event_types.sql
  ```
- **Tests:** Testcontainers spins a fresh Postgres and Flyway migrates it — your integration tests
  run against the real schema, not an H2 approximation.

## Entity ↔ table quick map
| Java entity | Table |
|-------------|-------|
| `EventType` | `event_type` |
| `Location` | `location` |
| `Event` | `event` |
| `TicketType` | `ticket_type` |
| `Order` | `orders` |
| `OrderItem` | `order_item` |
| `Payment` | `payment` |
| `StripeEvent` | `stripe_event` |
| `Ticket` | `ticket` |
| `TicketScan` | `ticket_scan` |
