# Passlify — Architecture & Conventions

> How the Spring Boot codebase is organized, the patterns to follow, and the cross-cutting
> decisions. Pair with [DOMAIN.md](./DOMAIN.md) (what) and [BUILD-SETUP.md](./BUILD-SETUP.md) (deps/config).

## 1. Stack

| Concern        | Choice                                   |
|----------------|------------------------------------------|
| Language       | Java 17 (LTS). Bump to 21 is trivial later. |
| Framework      | Spring Boot 3.3.x                        |
| Build          | Maven (wrapper `./mvnw`)                  |
| DB             | PostgreSQL 15                            |
| Persistence    | Spring Data JPA / Hibernate              |
| Migrations     | Flyway (versioned SQL)                    |
| Auth           | Spring Security OAuth2 Resource Server (Keycloak realm `passlify`) |
| Payments       | Stripe Java SDK                          |
| QR             | ZXing (`core` + `javase`)                |
| PDF            | OpenPDF or Flying Saucer (HTML→PDF)      |
| API docs       | springdoc-openapi                        |
| Mapping        | MapStruct (or hand-written; see §4.4)    |
| Boilerplate    | Lombok                                   |
| Testing        | JUnit 5, Spring Boot Test, Testcontainers (Postgres), MockMvc/WebTestClient |

## 2. Package-by-feature layout
Organize by **feature/domain module**, not by technical layer. Each module owns its
controller/service/repository/entity/dto. Shared plumbing lives in `common` and `config`.

```
hr.passlify
├── PasslifyApplication.java
├── config/
│   ├── SecurityConfig.java          # resource server, role mapping, /scan & /webhook rules
│   ├── OpenApiConfig.java
│   ├── JacksonConfig.java
│   └── StripeConfig.java            # Stripe API key bean, webhook secret
├── common/
│   ├── domain/BaseEntity.java       # @MappedSuperclass: UUID id, createdAt, updatedAt
│   ├── error/                       # GlobalExceptionHandler, ApiException, ProblemDetail
│   ├── money/Money.java             # minor-unit value object + helpers
│   └── security/CurrentUser.java    # resolves Keycloak sub/roles from SecurityContext
├── event/
│   ├── Event.java, EventType.java, Location.java
│   ├── EventStatus.java, Visibility.java
│   ├── EventRepository.java …
│   ├── EventService.java
│   ├── EventController.java         # /api/v1/events …
│   └── dto/
├── ticket/                          # TicketType (sellable) lives here
│   ├── TicketType.java, TicketKind.java
│   ├── TicketTypeRepository.java
│   ├── TicketTypeService.java
│   ├── TicketTypeController.java
│   └── dto/
├── order/
│   ├── Order.java, OrderItem.java, OrderStatus.java
│   ├── OrderRepository.java, OrderItemRepository.java
│   ├── CheckoutService.java         # pricing + inventory reservation + order creation
│   ├── OrderController.java
│   └── dto/
├── payment/
│   ├── Payment.java, PaymentStatus.java, PaymentProvider.java
│   ├── StripeEvent.java             # idempotency ledger
│   ├── PaymentRepository.java, StripeEventRepository.java
│   ├── stripe/
│   │   ├── StripeService.java       # create session, verify+parse webhook
│   │   ├── StripeWebhookController.java   # POST /api/v1/webhooks/stripe (raw body!)
│   │   └── StripeWebhookHandler.java      # dispatch by event type, idempotent
│   └── PaymentController.java       # create checkout session for an order
├── issuance/                        # issued tickets (the access unit)
│   ├── Ticket.java, TicketStatus.java
│   ├── TicketRepository.java
│   ├── TicketIssuanceService.java   # called on order PAID; idempotent
│   ├── qr/QrTokenService.java       # HMAC sign/verify
│   ├── qr/QrImageService.java       # ZXing render
│   ├── pdf/TicketPdfService.java
│   └── TicketController.java        # buyer fetches own tickets / pdf
├── scan/
│   ├── TicketScan.java, ScanResult.java
│   ├── TicketScanRepository.java
│   ├── ScanService.java             # the locked VALID→USED transaction
│   └── ScanController.java          # POST /api/v1/scan (OPERATOR)
└── notification/
    ├── EmailService.java            # ticket delivery
    └── templates/
```

> `TicketType` (sellable category) lives in `ticket/`; `Ticket` (issued unit) lives in
> `issuance/`. Keeping them in separate packages prevents the constant confusion the legacy
> code had between "variant" and "ticket".

## 3. Layering rules
- **Controller**: HTTP only — validate DTOs, map to/from service calls, no business logic.
- **Service**: business logic + `@Transactional` boundaries. Owns the rules in DOMAIN.md §4.
- **Repository**: Spring Data interfaces; custom queries via `@Query` or specifications.
- **Entity**: JPA mapping only; no business behavior beyond simple guards/derived getters.
- **DTO**: request/response records. **Never expose entities directly** over HTTP.
- Dependencies point inward: controller → service → repository. Services never call controllers.

## 4. Key conventions

### 4.1 IDs & base entity
- `BaseEntity` `@MappedSuperclass` with `@Id UUID id`, `@CreationTimestamp createdAt`,
  `@UpdateTimestamp updatedAt`. Assign UUID (v7 if you add a generator, else `UUID.randomUUID()`)
  in `@PrePersist` if null.

### 4.2 Money
- Always `int`/`long` minor units. Column names end in `Minor`. Add a `Money` value object for
  formatting/conversion at the edges. No `BigDecimal` in the domain except when talking to Stripe
  amounts (which are also integer minor units — convenient).

### 4.3 Transactions & concurrency
- The three correctness-critical operations are **single transactions**:
  - **Inventory reservation** — conditional `UPDATE … WHERE sold+qty <= total` (DOMAIN §4.2).
  - **Webhook processing** — insert `StripeEvent` PK first, then handle (DOMAIN §4.3).
  - **Scan** — `SELECT … FOR UPDATE` on the ticket, then `VALID→USED` (DOMAIN §4.7).
- Write integration tests that hammer these concurrently (Testcontainers).

### 4.4 Mapping
- Use MapStruct for entity↔DTO if you like generated mappers; hand-written static `from()`
  factory methods on DTO records are fine for a small surface. Pick one and be consistent.

### 4.5 Errors
- Throw typed exceptions (`NotFoundException`, `ConflictException`, `ValidationException`,
  `PaymentException`). A `@RestControllerAdvice` maps them to RFC-7807 `ProblemDetail`
  (Spring 6 has `ProblemDetail` built in). Never leak stack traces.

### 4.6 API
- Base path `/api/v1`. Plural nouns. Use proper status codes (201 on create, 409 on conflict,
  402/422 on payment issues). Cursor or page/size pagination on list endpoints.
- Public (no auth): event listing/detail, Stripe webhook (verified by signature instead).
- Authenticated: everything else, role-gated in `SecurityConfig`.

### 4.7 Stripe webhook specifics (easy to get wrong)
- The webhook endpoint needs the **raw request body** for signature verification — do not let
  Spring parse it to an object first. Read `byte[]`/`HttpServletRequest` input stream, or use a
  `@RequestBody String` and verify before parsing.
- Return **200 fast**; do heavy work after recording the event. Never 500 on a duplicate.

### 4.8 Security model
- Resource server validates Keycloak-issued JWTs (`issuer-uri` → realm).
- Map realm roles → Spring authorities with a `JwtAuthenticationConverter`
  (Keycloak puts roles under `realm_access.roles`).
- `@PreAuthorize("hasRole('ORGANIZER')")` etc. on service or controller methods.
- Organizer endpoints must additionally check **ownership** (`event.organizerId == currentUser.sub`).

## 5. Testing strategy
- **Unit**: services with mocked repos for pure logic (pricing, serial format, QR sign/verify).
- **Integration (Testcontainers Postgres)**: the three concurrency-critical flows, webhook
  idempotency (replay same event twice → one fulfillment), full checkout→pay→issue→scan happy path.
- **Web slice**: controller validation + security rules with MockMvc.
- Use Stripe's test mode + fixture event payloads for webhook tests (no live calls).

## 6. Configuration & secrets
- All config via `application.yml` + env vars (12-factor). No secrets in code.
- Required env: `DATABASE_*`, `KEYCLOAK_*`/issuer-uri, `STRIPE_API_KEY`,
  `STRIPE_WEBHOOK_SECRET`, `PASSLIFY_QR_SECRET`, `MAIL_*`, `APP_BASE_URL`.
- See [BUILD-SETUP.md](./BUILD-SETUP.md) for the full list and sample `application.yml`.