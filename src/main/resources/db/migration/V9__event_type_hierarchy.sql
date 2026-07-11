-- ============================================================================
-- V9 — Event type hierarchy (EVENT_DOMAIN_SPEC §19)
--
-- Turns the flat (category, type) reference data into a two-level hierarchy:
-- non-selectable category rows (parent) + selectable leaf types beneath them.
-- Existing leaf rows are preserved (event FKs stay valid); a parent row is
-- created per distinct category and the leaves are linked to it.
-- ============================================================================

-- 1. New columns (nullable / defaulted first).
ALTER TABLE event_type ADD COLUMN code       VARCHAR(80);
ALTER TABLE event_type ADD COLUMN name       VARCHAR(120);
ALTER TABLE event_type ADD COLUMN parent_id  UUID REFERENCES event_type (id) ON DELETE RESTRICT;
ALTER TABLE event_type ADD COLUMN selectable BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE event_type ADD COLUMN active     BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE event_type ADD COLUMN sort_order INT     NOT NULL DEFAULT 0;

-- The legacy (category, type) uniqueness would block parent rows whose type mirrors
-- the category (e.g. Other/Other); drop it now — the columns are removed in step 5.
ALTER TABLE event_type DROP CONSTRAINT uq_event_type_category_type;

-- 2. Create one non-selectable parent per distinct existing category.
INSERT INTO event_type (id, category, type, code, name, selectable, active, sort_order, created_at)
SELECT gen_random_uuid(),
       c.category,
       c.category,
       upper(regexp_replace(c.category, '[^a-zA-Z0-9]+', '_', 'g')),
       c.category,
       FALSE, TRUE, 0, now()
FROM (SELECT DISTINCT category FROM event_type) c;

-- 3. Link each existing leaf to its category parent and derive code/name.
UPDATE event_type leaf
SET name      = leaf.type,
    code      = parent.code || '.' || upper(regexp_replace(leaf.type, '[^a-zA-Z0-9]+', '_', 'g')),
    parent_id = parent.id
FROM event_type parent
WHERE parent.selectable = FALSE
  AND parent.name = leaf.category
  AND leaf.selectable = TRUE;

-- 4. Enforce integrity now that every row has code/name.
ALTER TABLE event_type ALTER COLUMN code SET NOT NULL;
ALTER TABLE event_type ALTER COLUMN name SET NOT NULL;
ALTER TABLE event_type ADD CONSTRAINT uq_event_type_code UNIQUE (code);
CREATE INDEX idx_event_type_parent ON event_type (parent_id);

-- 5. Drop the legacy flat columns (also drops uq_event_type_category_type).
ALTER TABLE event_type DROP COLUMN category;
ALTER TABLE event_type DROP COLUMN type;
