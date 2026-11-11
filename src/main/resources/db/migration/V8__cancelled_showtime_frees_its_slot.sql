-- A Cancelled Showtime no longer occupies its Auditorium, so a replacement may take its time slot.
ALTER TABLE showtime DROP CONSTRAINT ex_showtime_auditorium_time;

ALTER TABLE showtime ADD CONSTRAINT ex_showtime_auditorium_time
    EXCLUDE USING gist (auditorium_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
    WHERE (status <> 'CANCELLED');
