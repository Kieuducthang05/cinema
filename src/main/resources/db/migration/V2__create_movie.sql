CREATE TABLE movie (
    id               uuid         PRIMARY KEY,
    title            varchar(255) NOT NULL,
    description      text         NOT NULL,
    duration_minutes integer      NOT NULL CHECK (duration_minutes > 0),
    genre            varchar(255) NOT NULL,
    age_rating       varchar(4)   NOT NULL CHECK (age_rating IN ('P', 'K', 'T13', 'T16', 'T18')),
    release_date     date         NOT NULL,
    poster_url       text         NOT NULL
);
