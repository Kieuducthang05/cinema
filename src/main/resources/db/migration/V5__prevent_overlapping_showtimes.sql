CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE showtime ADD CONSTRAINT ex_showtime_auditorium_time
    EXCLUDE USING gist (auditorium_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&);
