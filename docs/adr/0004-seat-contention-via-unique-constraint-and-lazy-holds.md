# Seat contention via unique constraint and lazily expired Holds

English | [Tiếng Việt](0004-seat-contention-via-unique-constraint-and-lazy-holds.vi.md)

Double booking is prevented by a plain `UNIQUE(showtime_id, seat_id)` on booked seats, not by application locks: the insert that loses the race fails and is reported as a `409` naming the requested Seats that are taken; a transaction that loses a deadlock (overlapping lapsed-Hold deletes) is retried once first. To keep that constraint plain, Pending Bookings that are abandoned or Expired are deleted rather than kept with a status, and expiry is lazy: there is no scheduler. When someone books a Seat, the same transaction first deletes any lapsed Hold on it; seat maps filter by `expires_at > now()`; payment is a conditional update that only succeeds while the Hold is live.

The same pattern enforces "at most one live Pending Booking per Customer": a partial `UNIQUE(customer_id) WHERE status = 'PENDING'`, with the booking transaction first deleting that Customer's own lapsed Pending Booking. `now()` can't appear in an index predicate, so the lazy delete is what keeps a lapsed Hold from counting.

## Consequences

- No history of abandoned or Expired Bookings. Acceptable: nothing reads it.
- Lapsed Holds on Seats nobody re-books linger as rows until touched; they never block anyone, neither a new Booking nor deleting their Showtime.
- A Hold claims its Seats whether or not it is ever paid, so many Customer accounts can keep a Showtime's Seats held without buying. Accepted: countering it is anti-abuse work (rate limits, verified accounts), not a booking rule.
- Pessimistic/optimistic locking and queue-based booking were deliberately left out as phase 2 comparison points.
