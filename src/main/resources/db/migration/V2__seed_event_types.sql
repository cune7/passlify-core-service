-- ============================================================================
-- Seed reference data for event_type. Idempotent (ON CONFLICT DO NOTHING) so it
-- is safe even if the unique (category, type) rows already exist.
-- ============================================================================

INSERT INTO event_type (category, type) VALUES
    ('Music',       'Concert'),
    ('Music',       'Festival'),
    ('Music',       'Club Night'),
    ('Sport',       'Match'),
    ('Sport',       'Tournament'),
    ('Conference',  'Conference'),
    ('Conference',  'Workshop'),
    ('Conference',  'Meetup'),
    ('Theatre',     'Play'),
    ('Theatre',     'Musical'),
    ('Comedy',      'Stand-up'),
    ('Film',        'Screening'),
    ('Exhibition',  'Exhibition'),
    ('Family',      'Family Event'),
    ('Other',       'Other')
ON CONFLICT (category, type) DO NOTHING;
