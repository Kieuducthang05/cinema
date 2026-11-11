# Cinema Booking

English | [Tiếng Việt](GLOSSARY.vi.md)

Backend for a cinema chain: admins publish what is showing where, customers book seats for a specific screening.

## Language

### Catalogue

**Cinema**:
One physical venue of the chain, identified by name and address.
_Avoid_: Theater, branch, location

**Auditorium**:
A screening room inside exactly one Cinema, with a fixed seat layout.
_Avoid_: Room, hall, screen

**Seat**:
A fixed position in an Auditorium, identified by row letter and number (e.g. `C7`), with a Seat Type.
_Avoid_: Chair, place

**Seat Type**:
The class of a Seat (Standard, VIP or Couple), which affects its price. A Couple seat is one sellable unit for two people.
_Avoid_: Seat class, category

**Movie**:
A film the chain can screen, with a known running time.
_Avoid_: Film, title

**Showtime**:
One scheduled screening of a Movie in an Auditorium at a specific start time.
_Avoid_: Screening, session, show

**Cancelled**:
A Showtime an Admin called off, before it starts or while it is running, because of a problem on the cinema's side (broken projector, fire, power cut). The Admin must give a Cancellation Reason. Final: it is never restored. It can no longer be booked, its Pending Bookings disappear, and it drops out of what is showing, but it stays reachable from its Refunded Bookings so Customers can see what they were refunded for. It no longer occupies its Auditorium: a replacement Showtime may take its time slot.

**Cancellation Reason**:
Why a Showtime was Cancelled: one of Equipment Failure, Power Outage, Emergency or Other, plus a note, which Other requires and the rest may omit. Customers see it on the Cancelled Showtime.

**Base Price**:
The price of a Standard Seat for a given Showtime; VIP and Couple prices are derived from it by fixed surcharges.
_Avoid_: Ticket price, fare

### Booking

**Booking**:
A Customer's reservation of one or more Seats for a single Showtime. Starts Pending and becomes Confirmed once paid.
_Avoid_: Order, reservation, ticket

**Hold**:
The time-limited claim a Pending Booking has on its Seats; when it lapses the Seats are free again.
_Avoid_: Lock, reservation

**Pending**:
A Booking whose Hold is running and which has not been paid yet. A Customer can abandon it; it then disappears and its Seats are free.

**Expired**:
A Pending Booking whose Hold has lapsed. It can no longer be paid and no longer claims its Seats.

**Confirmed**:
A paid Booking. The Customer can never cancel it or get a refund; a Customer who can't attend passes it on themselves. Only the cinema cancelling its Showtime ends it, as Refunded.

**Refunded**:
A Confirmed Booking whose Showtime was Cancelled; its full price is returned. Final. Never triggered by, or on behalf of, a Customer.
_Avoid_: Cancelled (a Customer never cancels a Booking)

### People

**Account**:
The login identity (email + password) of a Customer or an Admin; its role says which.
_Avoid_: User, login, profile

**Customer**:
A registered person who books seats for Showtimes.
_Avoid_: User, client, member

**Admin**:
Staff who manage Cinemas, Auditoriums, Movies and Showtimes. Created out of band, never self-registered.
_Avoid_: Manager, staff, operator
