package vn.edu.uet.cinema.business.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.edu.uet.cinema.business.ConflictException;
import vn.edu.uet.cinema.business.NotFoundException;
import vn.edu.uet.cinema.business.showtime.PricedSeat;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeService;

public class BookingService {

	private static final Duration HOLD = Duration.ofMinutes(10);
	private static final int MAX_SEATS = 8;

	private final ShowtimeService showtimes;
	private final BookingRepository bookings;

	public BookingService(ShowtimeService showtimes, BookingRepository bookings) {
		this.showtimes = showtimes;
		this.bookings = bookings;
	}

	public Booking book(UUID customerId, UUID showtimeId, List<UUID> seatIds, Instant now) {
		if (seatIds.isEmpty() || seatIds.size() > MAX_SEATS) {
			throw new InvalidBookingException("A Booking has 1 to " + MAX_SEATS + " Seats");
		}
		if (Set.copyOf(seatIds).size() != seatIds.size()) {
			throw new InvalidBookingException("Duplicate seatIds");
		}
		Showtime showtime = showtimes.getScheduled(showtimeId);
		if (!now.isBefore(showtime.startsAt())) {
			throw new ConflictException("Showtime has already started");
		}
		Map<UUID, PricedSeat> seats = showtimes.pricedSeats(showtime);
		List<BookedSeat> booked = seatIds.stream().map(id -> {
			PricedSeat seat = seats.get(id);
			if (seat == null) {
				throw new InvalidBookingException("Seat " + id + " is not in the Showtime's Auditorium");
			}
			return new BookedSeat(id, seat.price());
		}).toList();
		bookings.findLivePending(customerId, now).ifPresent(pending -> {
			throw new ConflictException("Customer already has a Pending Booking " + pending.id());
		});
		return bookings.saveNew(new Booking(UUID.randomUUID(), customerId, showtimeId, BookingStatus.PENDING, now,
			now.plus(HOLD), booked), now);
	}

	/** The Customer's own Bookings, newest first. */
	public List<Booking> list(UUID customerId) {
		return bookings.findByCustomer(customerId);
	}

	/** Someone else's Booking is reported as not found. */
	public Booking get(UUID customerId, UUID id) {
		return bookings.findById(id).filter(booking -> booking.customerId().equals(customerId))
			.orElseThrow(() -> new NotFoundException("Booking not found"));
	}

	/** Simulated payment: always succeeds, so only the Hold decides. Paying a Confirmed Booking returns it. */
	public Booking pay(UUID customerId, UUID id, Instant now) {
		Booking booking = get(customerId, id);
		if (booking.statusAt(now) == BookingStatus.PENDING && bookings.confirm(id, now)) {
			return booking.confirmed();
		}
		// Not confirmed by us: it was already, or a concurrent payment just did, or the Hold has lapsed.
		Booking current = get(customerId, id);
		BookingStatus status = current.statusAt(now);
		if (status != BookingStatus.CONFIRMED) {
			throw new ConflictException("Booking is " + status + ", only a Pending Booking can be paid");
		}
		return current;
	}

	/** An Expired Booking can be abandoned too: it is removed like any lapsed Hold. */
	public void abandon(UUID customerId, UUID id) {
		if (get(customerId, id).status() == BookingStatus.PENDING && bookings.deletePending(id)) {
			return;
		}
		// Nothing deleted: it is Confirmed or Refunded, was paid just now, or is already gone (then this is 404).
		BookingStatus status = get(customerId, id).status();
		throw new ConflictException("Booking is " + status + ", only a Pending Booking can be abandoned");
	}
}
