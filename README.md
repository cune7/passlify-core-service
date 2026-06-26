# passlify-core-service

Backend for the Passlify event-ticketing platform — an entrio.hr-style flow built as a
modular monolith: **organizer creates an event with ticket types → buyer checks out and pays →
tickets are issued with signed QR codes → operator scans at the gate**. Refunds void tickets and
release inventory.

- **Stack:** Spring Boot 4.1 · Java 25 · PostgreSQL · Flyway · Spring Security (Keycloak OAuth2
  resource server) · Hibernate/JPA · ZXing (QR) · OpenPDF · Jakarta Mail.
- **Specs:** see [`passlify-spec/`](./passlify-spec) — `SCOPE`, `DOMAIN`, `ARCHITECTURE`,
  `DATABASE`, `API`, and the product blueprint `docs/passlify_ticketing_master_spec.md`.
- Money is stored as integer **minor units** (`*_minor`); currency is **per event** (default RSD).

---

## Prerequisites

- JDK 25 (the Maven wrapper `./mvnw` handles Maven itself)
- Docker (Desktop, or `colima start` on macOS) — for local infra and for the Testcontainers tests

---

## Local dev setup

Local infra (Postgres + Keycloak + Mailpit) is defined in [`docker-compose.yml`](./docker-compose.yml).
The app's defaults in `src/main/resources/application.yml` already point at these, so **no env vars
are required** for local dev.

### 1. Start infra

```bash
docker compose up -d
```

| Service  | Port(s)              | Details                                                        |
|----------|----------------------|---------------------------------------------------------------|
| postgres | `5432`               | db / user / pass = `passlify` / `passlify` / `passlifypassword` |
| keycloak | `8080`               | realm `passlify` auto-imported; admin console `admin` / `admin` |
| mailpit  | `1025` SMTP, `8025` UI | sent ticket emails appear at http://localhost:8025            |

Keycloak imports [`keycloak/passlify-realm.json`](./keycloak/passlify-realm.json) on first start
(~30–60s): roles `ORGANIZER` / `OPERATOR` / `ADMIN`, a public client `passlify-client`, and three
test users (all with password `password`):

| User         | Role      |
|--------------|-----------|
| `organizer1` | ORGANIZER |
| `operator1`  | OPERATOR  |
| `admin1`     | ADMIN     |

### 2. Run the app

Wait until Keycloak is ready (the app fetches the realm's OIDC config at boot), then:

```bash
./mvnw spring-boot:run
```

The app starts on **`:8081`**, Flyway migrates the schema, and JWTs are validated against the
Keycloak realm. Health: `GET http://localhost:8081/actuator/health`.

### 3. Get a token and call the API

```bash
# Fetch an organizer access token (direct access grant)
TOKEN=$(curl -s -X POST http://localhost:8080/realms/passlify/protocol/openid-connect/token \
  -d grant_type=password -d client_id=passlify-client \
  -d username=organizer1 -d password=password \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

# Create an event (organizer-scoped)
curl -s -X POST http://localhost:8081/api/v1/events \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Summer Fest 2026","startsAt":"2026-08-01T18:00:00Z","endsAt":"2026-08-01T23:00:00Z","visibility":"PUBLIC"}'
```

### End-to-end flow (manual)

1. `POST /api/v1/events` then `POST /api/v1/events/{id}/ticket-types` (organizer token).
2. `POST /api/v1/events/{id}/publish`.
3. `POST /api/v1/orders` — guest checkout (no token); inventory is reserved atomically.
4. `POST /api/v1/orders/{id}/payment-session` — returns a `checkoutUrl` + `sessionId` (mock provider).
5. Simulate the bank confirming payment:
   `POST /api/v1/webhooks/mock` with `{"type":"PAID","sessionId":"<sessionId>"}`
   (use `"FAILED"` to decline, `"REFUNDED"` to refund). Tickets are issued on PAID and emailed —
   check **Mailpit** at http://localhost:8025.
6. `GET /api/v1/orders/{id}/tickets`, `GET /api/v1/tickets/{id}/qr` (PNG), `/pdf`.
7. Scan at the gate (operator token): `POST /api/v1/scan` with `{"qrToken":"...","eventId":"..."}`.

> Free orders (all ticket types priced 0) skip payment — tickets are issued immediately on
> `POST /api/v1/orders`.

### Custom fields & per-attendee data

- `POST /api/v1/events/{eventId}/custom-fields` — define a field (`scope` = `PER_PURCHASE`
  for the buyer, `PER_ATTENDEE` for each ticket; `type`, `required`, `options` for SELECT).
- `GET /api/v1/events/{eventId}/custom-fields` · `PATCH`/`DELETE /api/v1/custom-fields/{id}`.
- A ticket type's `attendeeDataMode` (`BUYER_ONLY` | `EACH_TICKET`) decides whether attendee
  data is collected per ticket. The public event detail returns the field definitions so a
  checkout form can render them.
- At checkout, `buyer.fields` carries PER_PURCHASE values and each line's `attendees[]` (one per
  quantity unit for `EACH_TICKET`) carries name/email/phone + PER_ATTENDEE `fields`. Required
  fields and attendee counts are validated server-side; issued tickets link to their attendee.

### Organizer dashboard (organizer token, own events only)

- `GET /api/v1/events/{eventId}/sales-summary` — tickets issued/checked-in, paid orders, gross
  revenue, and a per-ticket-type breakdown.
- `GET /api/v1/events/{eventId}/orders` — paged orders for the event.
- `GET /api/v1/events/{eventId}/attendees` — paged attendee list (one row per ticket).
- `GET /api/v1/events/{eventId}/attendees/export` — attendee list as CSV download.
- `GET /api/v1/events/{eventId}/scan-summary` — live entry counts.

### Stop / reset

```bash
docker compose down          # stop
docker compose down -v       # stop and wipe the Postgres volume
```

---

## Tests

The integration tests use **Testcontainers** — they spin up their own throwaway Postgres, so they
need the Docker **daemon** running but **not** `docker compose up`. Keycloak and SMTP are stubbed in
tests.

```bash
./mvnw test
```

What they cover: schema migration + entity mapping validation, no-oversell under concurrency,
webhook idempotency, ticket issuance + signed-QR round-trip, concurrent double-scan prevention, and
refund void/release. `QrTokenServiceTest` is a pure unit test and runs without Docker.

The integration base (`AbstractIntegrationTest`) uses `@Testcontainers(disabledWithoutDocker = true)`,
so when no Docker daemon is reachable those tests are **skipped** (not failed) — `mvn install` stays
green. They run for real as soon as Docker is up.

### Troubleshooting Testcontainers

If `./mvnw test` reports `Skipped` for the integration tests, Testcontainers couldn't reach a
usable Docker daemon. Verify from a terminal:

```bash
docker info     # must show a real "Server Version" — not an error or an empty/400 response
docker version  # Client + Server API versions
docker run --rm hello-world
```

- `docker info` errors/hangs, or you saw a `400 Bad Request` with an empty body → Docker Desktop is
  still initializing or wedged. Wait for the whale icon to settle, or quit & relaunch it, then retry.
- `docker info` works in the terminal but the tests still skip when launched from the IDE → the IDE's
  Maven run isn't seeing the Docker socket. Easiest fix: run `./mvnw test` from the terminal. Or set
  the socket explicitly (Docker Desktop on macOS uses a non-standard path):

  ```bash
  export DOCKER_HOST="unix://$HOME/Library/Containers/com.docker.docker/Data/docker-cli.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ./mvnw test
  ```

---

## Configuration

All config is via `application.yml` + env vars (12-factor); local defaults work out of the box.
Notable overrides:

| Env var | Default | Purpose |
|---|---|---|
| `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_NAME` / `DATABASE_USER` / `DATABASE_PASSWORD` | localhost / 5432 / passlify / passlify / passlifypassword | Postgres connection |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8080/realms/passlify` | OAuth2 resource-server issuer |
| `PASSLIFY_QR_SECRET` | dev placeholder | HMAC key for signing ticket QR tokens — **set a real 32+ byte secret outside dev** |
| `PASSLIFY_DEFAULT_CURRENCY` | `RSD` | default currency for new events |
| `MAIL_HOST` / `MAIL_PORT` | localhost / 1025 | SMTP (Mailpit locally) |
| `PASSLIFY_MAIL_ENABLED` | `true` | toggle ticket-email delivery |
| `PORT` | `8081` | app HTTP port (8080 is Keycloak) |
