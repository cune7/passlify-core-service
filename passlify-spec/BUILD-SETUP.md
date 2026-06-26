# Passlify — Build Setup

> Everything needed to stand up a fresh Spring Boot project that matches the spec. Generate the
> skeleton, drop in these dependencies and config, then build the slices from [SCOPE.md](./SCOPE.md).

## 1. Generate the skeleton
Use Spring Initializr (web UI at start.spring.io or curl):

```bash
curl -fsSL https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=17 \
  -d groupId=hr.passlify -d artifactId=passlify-api \
  -d name=passlify-api -d packageName=hr.passlify \
  -d dependencies=web,data-jpa,postgresql,flyway,validation,security,oauth2-resource-server,actuator,lombok \
  -o passlify-api.zip && unzip passlify-api.zip -d passlify-api
```

This gives you `./mvnw` (Maven Wrapper — no global Maven install needed), `pom.xml`, and the
base package. Then add the extra dependencies below.

## 2. Extra Maven dependencies
The starter doesn't include Stripe / QR / PDF / OpenAPI / Testcontainers. Add to `pom.xml`:

```xml
<!-- Stripe -->
<dependency>
  <groupId>com.stripe</groupId>
  <artifactId>stripe-java</artifactId>
  <version>26.* (latest)</version>
</dependency>

<!-- QR generation (ZXing) -->
<dependency>
  <groupId>com.google.zxing</groupId>
  <artifactId>core</artifactId>
  <version>3.5.3</version>
</dependency>
<dependency>
  <groupId>com.google.zxing</groupId>
  <artifactId>javase</artifactId>
  <version>3.5.3</version>
</dependency>

<!-- PDF (pick one) -->
<dependency>
  <groupId>com.github.librepdf</groupId>
  <artifactId>openpdf</artifactId>
  <version>2.0.* (latest)</version>
</dependency>

<!-- OpenAPI / Swagger UI -->
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.6.* (latest)</version>
</dependency>

<!-- Email -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- (Optional) MapStruct -->
<dependency>
  <groupId>org.mapstruct</groupId>
  <artifactId>mapstruct</artifactId>
  <version>1.6.* (latest)</version>
</dependency>

<!-- Testing: Testcontainers -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

> Pin to the latest stable versions at build time. For MapStruct + Lombok together, add the
> `lombok-mapstruct-binding` annotation processor in `maven-compiler-plugin`.

## 3. `application.yml`
```yaml
spring:
  application:
    name: passlify-api
  datasource:
    url: jdbc:postgresql://${DATABASE_HOST:localhost}:${DATABASE_PORT:5432}/${DATABASE_NAME:passlify}
    username: ${DATABASE_USER:passlify}
    password: ${DATABASE_PASSWORD:passlifypassword}
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway owns the schema; never 'update' in real envs
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/passlify}

server:
  port: ${PORT:8081}              # 8080 is taken by Keycloak locally

stripe:
  api-key: ${STRIPE_API_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}

passlify:
  qr-secret: ${PASSLIFY_QR_SECRET}      # HMAC key for signing ticket QR tokens
  base-url: ${APP_BASE_URL:http://localhost:8081}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## 4. Environment variables
Reuse the legacy `.env` values where they overlap (same Keycloak realm + Stripe keys).

| Var                   | Example / source                                  |
|-----------------------|---------------------------------------------------|
| `DATABASE_HOST`       | `localhost` (or `db` in compose)                  |
| `DATABASE_PORT`       | `5432`                                            |
| `DATABASE_NAME`       | `passlify`                                         |
| `DATABASE_USER`       | `passlify`                                         |
| `DATABASE_PASSWORD`   | `passlifypassword`                                |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8080/realms/passlify`           |
| `STRIPE_API_KEY`      | from legacy `.env` `STRIPE_SECRET_KEY`            |
| `STRIPE_WEBHOOK_SECRET` | from legacy `.env`                              |
| `PASSLIFY_QR_SECRET`  | **new** — generate a 32+ byte random secret       |
| `APP_BASE_URL`        | `http://localhost:8081`                           |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `25` (smtp4dev for local)       |

Keycloak realm = `passlify`, client = `passlify-client` (matches legacy). For local dev,
Keycloak runs on `:8080` per the legacy `docker-compose.yml`.

## 5. Local infra
You can reuse the legacy `docker-compose.yml` (Postgres on 5432, Keycloak on 8080, smtp4dev on
:5000 web / :25 SMTP). Point `DATABASE_*` and `KEYCLOAK_ISSUER_URI` at those. The Java app runs
on `:8081` to avoid clashing with Keycloak.

> If you want a clean DB for the new service, create a separate database (e.g. `passlify_api`)
> rather than sharing the legacy schema.

## 6. Flyway
- Migrations live in `src/main/resources/db/migration` as `V1__baseline.sql`, `V2__...`.
- `V1` creates all tables in [DOMAIN.md](./DOMAIN.md) §2 with the indexes/constraints noted.
- Seed `EventType` reference data in a repeatable migration `R__seed_event_types.sql` or `V2`.
- Keep `ddl-auto: validate` so Hibernate verifies the entities match the migrated schema.

## 7. First boot checklist
1. `./mvnw spring-boot:run` → app starts on `:8081`.
2. Flyway applies `V1` → tables exist.
3. `GET /actuator/health` → `{"status":"UP"}`.
4. `GET /swagger-ui.html` → API docs render.
5. A request with a valid Keycloak JWT passes; without one → 401.
6. Stripe CLI: `stripe listen --forward-to localhost:8081/api/v1/webhooks/stripe` for local
   webhook testing.

## 8. Definition of done per slice
A slice (see SCOPE.md §"Suggested build order") is done when: endpoints work, DTOs validated,
errors return problem+json, **the relevant correctness test passes** (oversell / idempotency /
double-scan), and OpenAPI reflects the new endpoints.
