-- A Confirmed Booking becomes Refunded when its Showtime is Cancelled.
ALTER TABLE booking DROP CONSTRAINT booking_status_check;

ALTER TABLE booking ADD CONSTRAINT ck_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REFUNDED'));
