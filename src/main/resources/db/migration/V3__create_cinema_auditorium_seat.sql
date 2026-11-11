CREATE TABLE cinema (
    id      uuid         PRIMARY KEY,
    name    varchar(255) NOT NULL,
    address text         NOT NULL
);

CREATE TABLE auditorium (
    id            uuid         PRIMARY KEY,
    cinema_id     uuid         NOT NULL,
    name          varchar(255) NOT NULL,
    rows          integer      NOT NULL CHECK (rows BETWEEN 1 AND 26),
    seats_per_row integer      NOT NULL CHECK (seats_per_row > 0),
    CONSTRAINT fk_auditorium_cinema FOREIGN KEY (cinema_id) REFERENCES cinema (id) ON DELETE RESTRICT,
    CONSTRAINT uq_auditorium_cinema_name UNIQUE (cinema_id, name)
);

CREATE TABLE seat (
    id            uuid        PRIMARY KEY,
    auditorium_id uuid        NOT NULL,
    row_letter    varchar(1)  NOT NULL,
    seat_number   integer     NOT NULL CHECK (seat_number > 0),
    type          varchar(8)  NOT NULL CHECK (type IN ('STANDARD', 'VIP', 'COUPLE')),
    CONSTRAINT fk_seat_auditorium FOREIGN KEY (auditorium_id) REFERENCES auditorium (id),
    UNIQUE (auditorium_id, row_letter, seat_number)
);
