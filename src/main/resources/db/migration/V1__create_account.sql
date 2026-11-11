CREATE TABLE account (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(255) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
