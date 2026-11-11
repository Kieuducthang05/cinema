-- A Cancelled Showtime keeps its row: its Refunded Bookings still point at it.
ALTER TABLE showtime
    ADD COLUMN status              varchar(10)  NOT NULL DEFAULT 'SCHEDULED'
        CONSTRAINT ck_showtime_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    ADD COLUMN cancellation_reason varchar(20)
        CONSTRAINT ck_showtime_cancellation_reason
            CHECK (cancellation_reason IN ('EQUIPMENT_FAILURE', 'POWER_OUTAGE', 'EMERGENCY', 'OTHER')),
    ADD COLUMN cancellation_note   varchar(200),
    -- Only a Cancelled Showtime has a Cancellation Reason, and Other always comes with a note.
    ADD CONSTRAINT ck_showtime_cancellation CHECK (
        (status = 'SCHEDULED' AND cancellation_reason IS NULL AND cancellation_note IS NULL)
        OR (status = 'CANCELLED' AND cancellation_reason IS NOT NULL
            AND (cancellation_reason <> 'OTHER' OR cancellation_note IS NOT NULL)));
