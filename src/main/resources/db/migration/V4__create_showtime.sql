CREATE TABLE showtime (
    id            uuid           PRIMARY KEY,
    movie_id      uuid           NOT NULL CONSTRAINT fk_showtime_movie REFERENCES movie (id) ON DELETE RESTRICT,
    auditorium_id uuid           NOT NULL CONSTRAINT fk_showtime_auditorium REFERENCES auditorium (id) ON DELETE RESTRICT,
    starts_at     timestamptz    NOT NULL,
    ends_at       timestamptz    NOT NULL,
    base_price    bigint         NOT NULL CHECK (base_price > 0),
    CONSTRAINT ck_showtime_time CHECK (ends_at > starts_at)
);

CREATE INDEX ix_showtime_auditorium_time ON showtime (auditorium_id, starts_at, ends_at);
CREATE INDEX ix_showtime_starts_at ON showtime (starts_at);
