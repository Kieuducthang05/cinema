-- Load-test seed data (issue #18): 3 cinemas x 4 auditoriums (10 rows x 12 seats),
-- 20 movies, 75 future days of showtimes and 6,000 CUSTOMER accounts that all
-- share one prehashed bcrypt password. k6/lib/showtimes.js must match the day count.
-- Book, contention and race log in disjoint blocks of 2,000 Customers.
--
-- Run AFTER the service has started (Flyway must have applied all migrations) and only
-- against a fresh database: the inserts are not idempotent.
--
-- Shared credentials: loadtest-{1..6000}@loadtest.local / loadtest-password
-- (every account stores the same bcrypt hash; the raw password is never stored).
BEGIN;

-- ---------------------------------------------------------------- cinemas ---
CREATE TEMP TABLE seed_cinema AS
SELECT g.id, g.n AS n,
       'Loadtest Cinema ' || g.n        AS name,
       'Address ' || g.n || ', Hanoi'   AS address
FROM (SELECT gen_random_uuid() AS id, n FROM generate_series(1, 3) AS n) AS g;

INSERT INTO cinema (id, name, address) SELECT id, name, address FROM seed_cinema;

-- ------------------------------------------------------------ auditoriums ---
CREATE TEMP TABLE seed_aud AS
SELECT gen_random_uuid()   AS id,
       c.id                AS cinema_id,
       'Auditorium ' || c.n || '.' || a.n AS name
FROM seed_cinema AS c
CROSS JOIN generate_series(1, 4) AS a(n);

INSERT INTO auditorium (id, cinema_id, name, rows, seats_per_row)
SELECT id, cinema_id, name, 10, 12 FROM seed_aud;

-- ------------------------------------------------------------------ seats ---
-- Rows A..G STANDARD, H..I VIP, J COUPLE (one sellable unit for two people).
INSERT INTO seat (id, auditorium_id, row_letter, seat_number, type)
SELECT gen_random_uuid(),
       a.id,
       chr(64 + r.r),
       s.n,
       CASE WHEN r.r BETWEEN 8 AND 9 THEN 'VIP'
            WHEN r.r = 10 THEN 'COUPLE'
            ELSE 'STANDARD' END
FROM seed_aud AS a
CROSS JOIN generate_series(1, 10) AS r(r)
CROSS JOIN generate_series(1, 12) AS s(n);

-- ----------------------------------------------------------------- movies ---
-- Durations stay <= 170 min so the 3-hour slots below can never overlap.
INSERT INTO movie (id, title, description, duration_minutes, genre, age_rating, release_date, poster_url)
SELECT gen_random_uuid(),
       'Loadtest Movie ' || g,
       'Load-test filler movie number ' || g || '.',
       80 + (g * 5) % 90,                       -- 80 .. 165 minutes
       'Genre ' || (1 + g % 5),
       (ARRAY['P', 'K', 'T13', 'T16', 'T18'])[1 + g % 5],
       CURRENT_DATE - g,
       'https://example.com/poster/' || g || '.jpg'
FROM generate_series(1, 20) AS g;

-- -------------------------------------------------------------- showtimes ---
-- 5 slots a day (09:00, 12:00, 15:00, 18:00, 21:00 Vietnam time) per
-- auditorium for the 75 days after today (VN local date), so every showtime is
-- strictly in the future at seed time. 3-hour slots with <= 165-minute movies
-- cannot overlap, satisfying the V5 exclusion constraint.
WITH day AS (
    SELECT ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + d) AS vn_date,
           d
    FROM generate_series(1, 75) AS d
), slot AS (
    SELECT d.vn_date, d.d AS day_no, s.n AS slot_no,
           (d.vn_date + (s.n * 3) * interval '1 hour'
                + interval '9 hours') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS starts_at
    FROM day AS d CROSS JOIN generate_series(0, 4) AS s(n)
)
, pick AS (
    SELECT sl.*, a.id AS auditorium_id,
           row_number() OVER (ORDER BY sl.day_no, sl.slot_no, a.rn) % 20 AS movie_idx
    FROM slot AS sl
    CROSS JOIN (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM seed_aud) AS a
), movies_indexed AS (
    SELECT id, duration_minutes, row_number() OVER (ORDER BY id) - 1 AS idx FROM movie
)
INSERT INTO showtime (id, movie_id, auditorium_id, starts_at, ends_at, base_price)
SELECT gen_random_uuid(),
       mi.id,
       p.auditorium_id,
       p.starts_at,
       p.starts_at + make_interval(mins => mi.duration_minutes),
       100000
FROM pick AS p
JOIN movies_indexed AS mi ON mi.idx = p.movie_idx;

-- --------------------------------------------------------------- accounts ---
-- 6,000 Customers sharing one valid prehashed bcrypt password
-- ("loadtest-password"). Spring Security's BCryptPasswordEncoder accepts the
-- $2a$ format, so no application change is needed.
INSERT INTO account (id, email, password_hash, full_name, role)
SELECT gen_random_uuid(),
       'loadtest-' || g || '@loadtest.local',
       '$2a$10$HxPCwAysCl/cAKxpANoI2uw2Re2f4s8cSN4ZDMkOkP.EVhE1FO6yi',
       'Loadtest Customer ' || g,
       'CUSTOMER'
FROM generate_series(1, 6000) AS g;

COMMIT;
