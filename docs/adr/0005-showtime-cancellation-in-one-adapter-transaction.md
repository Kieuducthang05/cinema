# Showtime cancellation in one adapter transaction

English | [Tiếng Việt](0005-showtime-cancellation-in-one-adapter-transaction.vi.md)

Cancelling a Showtime changes the Showtime and every Booking of it: its Pending Bookings are deleted and its Confirmed ones become Refunded. That breaks the rule that each use case saves exactly one aggregate, and deliberately so: a Showtime left Cancelled with a Confirmed Booking still on it would mean a Customer paid for a screening that won't happen and was never refunded. So `ShowtimeRepository.cancel` does all of it in one transaction in the persistence adapter, as `deleteById` already does when it removes lapsed Holds. The business layer still has no transactions: the service validates, then calls one repository method.

The statements run in this order, and the order is what makes concurrent payment safe:

1. `UPDATE showtime SET status = 'CANCELLED' … WHERE status = 'SCHEDULED' AND ends_at > now`. No row changed means already Cancelled or ended (`409`). This also serialises two Admins cancelling at once.
2. Delete the Showtime's Pending Bookings (Expired ones are stored as Pending too).
3. Turn its Confirmed Bookings Refunded.

A payment is a conditional update of the Booking row (ADR 0004). If it takes the row lock first, step 2 waits, re-checks the row after the payment commits, finds it Confirmed and skips it, and step 3 refunds it. If step 2 goes first, the payment finds no row. With steps 2 and 3 swapped, a payment landing between them would leave a Confirmed Booking on a Cancelled Showtime.

A new Booking races the cancel differently: `BookingService.book` checks the status first, but a cancel can commit between that check and the insert, and the Booking's foreign key does not stop it. The key-share lock the foreign key check takes doesn't conflict with an update of non-key columns. So the booking transaction starts with `SELECT status FROM showtime WHERE id = ? FOR SHARE` and refuses a Cancelled Showtime. Either the cancel's step 1 waits for the booking to commit and step 2 then deletes it, or the cancel committed first and the booking sees `CANCELLED`. Share locks don't conflict with each other, so concurrent bookings of the same Showtime don't wait on one another.

## Consequences

- Cancelling one Showtime locks its Booking rows and briefly blocks new bookings of that Showtime only.
- A reader who removes the `FOR SHARE`, or reorders the cancel's statements, reopens a race that ordinary tests won't show. `BookingPersistenceTest` has a randomised book/pay/cancel test that catches both.
- A Cancelled Showtime with Refunded Bookings can never be deleted (the foreign key is `RESTRICT`), which keeps it readable from those Bookings.
