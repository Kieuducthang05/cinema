-- ADR 0004: Pending Bookings that are abandoned or Expired are deleted, never kept with a status,
-- so plain unique constraints decide seat contention and "one live Pending Booking per Customer".
CREATE TABLE booking (
    id          uuid        PRIMARY KEY,
    customer_id uuid        NOT NULL CONSTRAINT fk_booking_customer REFERENCES account (id),
    showtime_id uuid        NOT NULL CONSTRAINT fk_booking_showtime REFERENCES showtime (id) ON DELETE RESTRICT,
    status      varchar(10) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED')),
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    CONSTRAINT uq_booking_id_showtime UNIQUE (id, showtime_id)
);

CREATE UNIQUE INDEX uq_booking_customer_pending ON booking (customer_id) WHERE status = 'PENDING';

CREATE TABLE booked_seat (
    booking_id  uuid   NOT NULL,
    showtime_id uuid   NOT NULL,
    seat_id     uuid   NOT NULL CONSTRAINT fk_booked_seat_seat REFERENCES seat (id),
    price       bigint NOT NULL CHECK (price > 0),
    PRIMARY KEY (booking_id, seat_id),
    -- Keeps showtime_id equal to its Booking's, so the unique constraint below is sound.
    CONSTRAINT fk_booked_seat_booking FOREIGN KEY (booking_id, showtime_id)
        REFERENCES booking (id, showtime_id) ON DELETE CASCADE,
    CONSTRAINT uq_booked_seat_showtime_seat UNIQUE (showtime_id, seat_id)
);
