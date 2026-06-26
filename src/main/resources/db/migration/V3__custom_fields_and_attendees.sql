-- ============================================================================
-- V3: per-attendee data + custom fields (master spec §5.9 / §6.7 / §13).
--   * custom_field: organizer-defined fields per event, scoped PER_PURCHASE
--     (buyer, once) or PER_ATTENDEE (per ticket).
--   * ticket_type.attendee_data_mode: BUYER_ONLY vs EACH_TICKET.
--   * attendee: one row per ticket when EACH_TICKET; holds name/email/phone +
--     custom field values (jsonb).
--   * orders.buyer_fields: PER_PURCHASE custom values for the buyer (jsonb).
--   * ticket.attendee_id: links an issued ticket to its attendee.
-- Field values are jsonb keyed by field_key; mapped as String in JPA and
-- (de)serialized in the service (avoids Hibernate's JSON FormatMapper).
-- ============================================================================

ALTER TABLE ticket_type
    ADD COLUMN attendee_data_mode VARCHAR(20) NOT NULL DEFAULT 'BUYER_ONLY';
ALTER TABLE ticket_type
    ADD CONSTRAINT ck_tt_attendee_mode CHECK (attendee_data_mode IN ('BUYER_ONLY', 'EACH_TICKET'));

ALTER TABLE orders
    ADD COLUMN buyer_fields JSONB;

CREATE TABLE custom_field (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID         NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    field_key   VARCHAR(60)  NOT NULL,
    label       VARCHAR(200) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    scope       VARCHAR(20)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    options     JSONB,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_custom_field UNIQUE (event_id, field_key),
    CONSTRAINT ck_cf_type  CHECK (type IN ('TEXT','TEXTAREA','EMAIL','PHONE','NUMBER','DATE','SELECT','CHECKBOX')),
    CONSTRAINT ck_cf_scope CHECK (scope IN ('PER_PURCHASE','PER_ATTENDEE'))
);
CREATE INDEX idx_custom_field_event ON custom_field (event_id);

CREATE TABLE attendee (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    ticket_type_id UUID         NOT NULL REFERENCES ticket_type (id),
    name           VARCHAR(256),
    email          VARCHAR(320),
    phone          VARCHAR(40),
    fields         JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendee_order ON attendee (order_id);
CREATE INDEX idx_attendee_order_tt ON attendee (order_id, ticket_type_id);

ALTER TABLE ticket
    ADD COLUMN attendee_id UUID REFERENCES attendee (id) ON DELETE SET NULL;
CREATE INDEX idx_ticket_attendee ON ticket (attendee_id);
