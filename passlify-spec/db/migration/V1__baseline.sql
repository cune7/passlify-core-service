-- ============================================================================
-- Passlify V1 baseline schema
-- Postgres 15+. Matches DOMAIN.md §2/§3. Columns are snake_case (Spring Boot
-- default physical naming strategy maps camelCase entity fields to these).
-- Enums are modelled as varchar + CHECK so they line up with JPA
-- @Enumerated(EnumType.STRING) and `spring.jpa.hibernate.ddl-auto=validate`.
-- Money is always integer minor units (cents). All timestamps are timestamptz.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;     -- case-insensitive event slug

-- ----------------------------------------------------------------------------
-- Lookup / reference
-- ----------------------------------------------------------------------------
CREATE TABLE event_type (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category    VARCHAR(80)  NOT NULL,
    type        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_type_category_type UNIQUE (category, type)
);

CREATE TABLE location (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_name   VARCHAR(255) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    city         VARCHAR(120) NOT NULL,
    country      VARCHAR(2)   NOT NULL,           -- ISO-3166-1 alpha-2
    postal_code  VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_location UNIQUE (venue_name, address, city, country, postal_code)
);

-- ----------------------------------------------------------------------------
-- Event
-- ----------------------------------------------------------------------------
CREATE TABLE event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            CITEXT,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    cover_image_url VARCHAR(2048),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    visibility      VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE',
    starts_at       TIMESTAMPTZ  NOT NULL,
    ends_at         TIMESTAMPTZ  NOT NULL,
    event_type_id   UUID         REFERENCES event_type (id) ON DELETE SET NULL,
    location_id     UUID         REFERENCES location (id)   ON DELETE SET NULL,
    organizer_id    VARCHAR(64)  NOT NULL,        -- Keycloak sub
    capacity        INT,
    tags            TEXT[],
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_slug      UNIQUE (slug),
    CONSTRAINT ck_event_status     CHECK (status IN ('DRAFT','PUBLISHED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_event_visibility CHECK (visibility IN ('PUBLIC','PRIVATE')),
    CONSTRAINT ck_event_dates      CHECK (ends_at > starts_at),
    CONSTRAINT ck_event_capacity   CHECK (capacity IS NULL OR capacity >= 0)
);

CREATE INDEX idx_event_organizer ON event (organizer_id);
CREATE INDEX idx_event_status     ON event (status);
CREATE INDEX idx_event_starts_at  ON event (starts_at);

-- ----------------------------------------------------------------------------
-- Ticket type (sellable category within an event; legacy "TicketVariant")
-- ----------------------------------------------------------------------------
CREATE TABLE ticket_type (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    description    TEXT,
    price_minor    INT          NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'EUR',
    total_quantity INT          NOT NULL,
    sold_quantity  INT          NOT NULL DEFAULT 0,
    max_per_order  INT          NOT NULL DEFAULT 10,
    sales_start_at TIMESTAMPTZ,
    sales_end_at   TIMESTAMPTZ,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    kind           VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_USE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tt_price        CHECK (price_minor >= 0),
    CONSTRAINT ck_tt_total        CHECK (total_quantity > 0),
    CONSTRAINT ck_tt_sold         CHECK (sold_quantity >= 0 AND sold_quantity <= total_quantity),
    CONSTRAINT ck_tt_max_per_order CHECK (max_per_order > 0),
    CONSTRAINT ck_tt_currency     CHECK (currency = 'EUR'),
    CONSTRAINT ck_tt_kind         CHECK (kind IN ('SINGLE_USE','MULTI_DAY_PASS','SEASON_PASS','MEMBERSHIP')),
    CONSTRAINT ck_tt_sales_window CHECK (sales_end_at IS NULL OR sales_start_at IS NULL OR sales_end_at > sales_start_at)
);

CREATE INDEX idx_ticket_type_event ON ticket_type (event_id);

-- ----------------------------------------------------------------------------
-- Order + order items
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING_PAYMENT',
    customer_id         VARCHAR(64),               -- Keycloak sub; null = guest
    customer_email      VARCHAR(320) NOT NULL,
    customer_name       VARCHAR(256),
    currency            CHAR(3)      NOT NULL DEFAULT 'EUR',
    subtotal_minor      INT          NOT NULL DEFAULT 0,
    discount_minor      INT          NOT NULL DEFAULT 0,
    tax_minor           INT          NOT NULL DEFAULT 0,
    total_minor         INT          NOT NULL,
    provider            VARCHAR(50),
    provider_intent_id  VARCHAR(255),
    return_url          VARCHAR(1024),
    paid_at             TIMESTAMPTZ,
    meta                JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_status   CHECK (status IN ('DRAFT','PENDING_PAYMENT','PAID','FAILED','CANCELLED','EXPIRED','REFUNDED','PARTIALLY_REFUNDED')),
    CONSTRAINT ck_order_currency CHECK (currency = 'EUR'),
    CONSTRAINT ck_order_amounts  CHECK (subtotal_minor >= 0 AND discount_minor >= 0 AND tax_minor >= 0 AND total_minor >= 0)
);

CREATE INDEX idx_orders_customer   ON orders (customer_id);
CREATE INDEX idx_orders_status     ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

CREATE TABLE order_item (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    ticket_type_id    UUID NOT NULL REFERENCES ticket_type (id),
    quantity          INT  NOT NULL,
    unit_price_minor  INT  NOT NULL,   -- snapshot at purchase time
    total_price_minor INT  NOT NULL,   -- unit_price_minor * quantity
    meta              JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_oi_quantity CHECK (quantity > 0),
    CONSTRAINT ck_oi_prices   CHECK (unit_price_minor >= 0 AND total_price_minor >= 0)
);

CREATE INDEX idx_order_item_order       ON order_item (order_id);
CREATE INDEX idx_order_item_ticket_type ON order_item (ticket_type_id);

-- ----------------------------------------------------------------------------
-- Payment + Stripe event idempotency ledger
-- ----------------------------------------------------------------------------
CREATE TABLE payment (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                 UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    provider                 VARCHAR(20)  NOT NULL DEFAULT 'STRIPE',
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    amount_minor             INT          NOT NULL,
    currency                 CHAR(3)      NOT NULL DEFAULT 'EUR',
    stripe_session_id        VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id         VARCHAR(255),
    stripe_customer_id       VARCHAR(255),
    refunded_minor           INT          NOT NULL DEFAULT 0,
    metadata                 JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_provider CHECK (provider IN ('STRIPE','PAYPAL','COINBASE')),
    CONSTRAINT ck_payment_status   CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED','PARTIALLY_REFUNDED')),
    CONSTRAINT ck_payment_amounts  CHECK (amount_minor >= 0 AND refunded_minor >= 0 AND refunded_minor <= amount_minor)
);

CREATE INDEX idx_payment_order              ON payment (order_id);
CREATE INDEX idx_payment_session            ON payment (stripe_session_id);
CREATE INDEX idx_payment_intent             ON payment (stripe_payment_intent_id);
CREATE INDEX idx_payment_charge             ON payment (stripe_charge_id);

-- The dedup guard: every received webhook event is recorded before processing.
CREATE TABLE stripe_event (
    id           VARCHAR(255) PRIMARY KEY,   -- Stripe event id (evt_...)
    type         VARCHAR(120) NOT NULL,
    payload      JSONB,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

-- ----------------------------------------------------------------------------
-- Issued tickets + scan audit log
-- ----------------------------------------------------------------------------
CREATE TABLE ticket (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID         NOT NULL REFERENCES orders (id),
    order_item_id     UUID         NOT NULL REFERENCES order_item (id),
    ticket_type_id    UUID         NOT NULL REFERENCES ticket_type (id),
    event_id          UUID         NOT NULL REFERENCES event (id),
    owner_customer_id VARCHAR(64),
    owner_email       VARCHAR(320),
    serial_number     VARCHAR(64)  NOT NULL,
    qr_token          VARCHAR(512) NOT NULL,
    status            VARCHAR(10)  NOT NULL DEFAULT 'VALID',
    attendee_name     VARCHAR(256),
    issued_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    used_at           TIMESTAMPTZ,
    scan_count        INT          NOT NULL DEFAULT 0,
    meta              JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_serial   UNIQUE (serial_number),
    CONSTRAINT uq_ticket_qr_token UNIQUE (qr_token),
    CONSTRAINT ck_ticket_status   CHECK (status IN ('VALID','USED','VOID')),
    CONSTRAINT ck_ticket_scan_cnt CHECK (scan_count >= 0)
);

CREATE INDEX idx_ticket_event       ON ticket (event_id);
CREATE INDEX idx_ticket_order       ON ticket (order_id);
CREATE INDEX idx_ticket_order_item  ON ticket (order_item_id);
CREATE INDEX idx_ticket_ticket_type ON ticket (ticket_type_id);
CREATE INDEX idx_ticket_owner       ON ticket (owner_customer_id);

CREATE TABLE ticket_scan (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID        REFERENCES ticket (id) ON DELETE SET NULL,  -- null when token didn't resolve
    event_id    UUID        REFERENCES event (id)  ON DELETE SET NULL,
    scanned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    result      VARCHAR(10) NOT NULL,
    reason      VARCHAR(40),
    scanned_by  VARCHAR(64),
    gate        VARCHAR(80),
    CONSTRAINT ck_scan_result CHECK (result IN ('ALLOWED','DENIED'))
);

CREATE INDEX idx_ticket_scan_ticket ON ticket_scan (ticket_id);
CREATE INDEX idx_ticket_scan_event  ON ticket_scan (event_id);
