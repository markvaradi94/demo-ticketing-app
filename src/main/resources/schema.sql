CREATE TABLE IF NOT EXISTS venues (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    capacity integer NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    venue_id varchar(255) REFERENCES venues(id),
    start_time timestamp NOT NULL,
    booked_seats integer NOT NULL DEFAULT 0
);
