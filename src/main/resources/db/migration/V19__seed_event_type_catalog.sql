-- ============================================================================
-- V19 — Seed the real event-type catalog (EVENT_DOMAIN_SPEC §19.1)
--
-- The V2/V9 seed used a provisional catalog (Theatre/Comedy/Film/Family/…). This
-- migration replaces it with the §19.1 catalog: six non-selectable categories and
-- their selectable leaves. Idempotent via ON CONFLICT (code); safe to re-run.
--
-- Obsolete types are DEACTIVATED, not deleted (§19.2), so any event already
-- referencing one keeps a valid FK — they just disappear from the organizer picker
-- (which lists active types only). Codes shared with the old seed
-- (MUSIC, SPORT, OTHER, MUSIC.FESTIVAL, OTHER.OTHER) are converted in place.
-- ============================================================================

-- 1. Categories (non-selectable headings). Upsert so shared codes are reused.
INSERT INTO event_type (id, code, name, parent_id, selectable, active, sort_order, created_at) VALUES
    (gen_random_uuid(), 'SPORT',         'Sport',         NULL, FALSE, TRUE, 1, now()),
    (gen_random_uuid(), 'MUSIC',         'Music',         NULL, FALSE, TRUE, 2, now()),
    (gen_random_uuid(), 'CULTURE',       'Culture',       NULL, FALSE, TRUE, 3, now()),
    (gen_random_uuid(), 'BUSINESS',      'Business',      NULL, FALSE, TRUE, 4, now()),
    (gen_random_uuid(), 'OTHER',         'Other',         NULL, FALSE, TRUE, 5, now()),
    (gen_random_uuid(), 'PRIVATE_EVENT', 'Private event', NULL, FALSE, TRUE, 6, now())
ON CONFLICT (code) DO UPDATE
    SET name       = EXCLUDED.name,
        parent_id  = NULL,
        selectable = FALSE,
        active     = TRUE,
        sort_order = EXCLUDED.sort_order;

-- 2. Leaves (selectable), linked to their category by code.
INSERT INTO event_type (id, code, name, parent_id, selectable, active, sort_order, created_at)
SELECT gen_random_uuid(), v.code, v.name, p.id, TRUE, TRUE, v.sort_order, now()
FROM (VALUES
    ('SPORT.PAINTBALL',            'Paintball',        'SPORT',         1),
    ('SPORT.RAFTING',              'Rafting',          'SPORT',         2),
    ('SPORT.RUNNING',              'Running / Marathon', 'SPORT',       3),
    ('SPORT.TOURNAMENTS',          'Tournaments',      'SPORT',         4),
    ('MUSIC.LIVE_CONCERT',         'Live concert',     'MUSIC',         1),
    ('MUSIC.FESTIVAL',             'Festival',         'MUSIC',         2),
    ('MUSIC.DJ_PARTY',             'DJ party',         'MUSIC',         3),
    ('MUSIC.PARTY',                'Party',            'MUSIC',         4),
    ('CULTURE.EXHIBITION',         'Exhibition',       'CULTURE',       1),
    ('CULTURE.MOVIE',              'Movie',            'CULTURE',       2),
    ('CULTURE.SHOW',               'Show',             'CULTURE',       3),
    ('CULTURE.STANDUP',            'Stand-up comedy',  'CULTURE',       4),
    ('CULTURE.QUIZZES',            'Quizzes',          'CULTURE',       5),
    ('BUSINESS.CONFERENCE',        'Conference',       'BUSINESS',      1),
    ('BUSINESS.SEMINAR',           'Seminar',          'BUSINESS',      2),
    ('OTHER.NETWORKING',           'Networking',       'OTHER',         1),
    ('OTHER.WORKSHOP',             'Workshop',         'OTHER',         2),
    ('OTHER.SEASON',               'Season',           'OTHER',         3),
    ('OTHER.OTHER',                'Other',            'OTHER',         4),
    ('PRIVATE_EVENT.PRIVATE_EVENT','Private event',    'PRIVATE_EVENT', 1)
) AS v(code, name, parent_code, sort_order)
JOIN event_type p ON p.code = v.parent_code
ON CONFLICT (code) DO UPDATE
    SET name       = EXCLUDED.name,
        parent_id  = EXCLUDED.parent_id,
        selectable = TRUE,
        active     = TRUE,
        sort_order = EXCLUDED.sort_order;

-- 3. Deactivate anything outside the §19.1 catalog (keep FKs valid; do not delete).
UPDATE event_type SET active = FALSE
WHERE code NOT IN (
    'SPORT', 'MUSIC', 'CULTURE', 'BUSINESS', 'OTHER', 'PRIVATE_EVENT',
    'SPORT.PAINTBALL', 'SPORT.RAFTING', 'SPORT.RUNNING', 'SPORT.TOURNAMENTS',
    'MUSIC.LIVE_CONCERT', 'MUSIC.FESTIVAL', 'MUSIC.DJ_PARTY', 'MUSIC.PARTY',
    'CULTURE.EXHIBITION', 'CULTURE.MOVIE', 'CULTURE.SHOW', 'CULTURE.STANDUP', 'CULTURE.QUIZZES',
    'BUSINESS.CONFERENCE', 'BUSINESS.SEMINAR',
    'OTHER.NETWORKING', 'OTHER.WORKSHOP', 'OTHER.SEASON', 'OTHER.OTHER',
    'PRIVATE_EVENT.PRIVATE_EVENT'
);
