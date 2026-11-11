package vn.edu.uet.cinema.persistence.booking;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import vn.edu.uet.cinema.business.booking.BookedSeat;
import vn.edu.uet.cinema.business.booking.Booking;
import vn.edu.uet.cinema.business.booking.BookingStatus;

@Entity
@Table(name = "booking")
class BookingEntity {

	@Id
	private UUID id;
	@Column(name = "customer_id")
	private UUID customerId;
	@Column(name = "showtime_id")
	private UUID showtimeId;
	@Enumerated(EnumType.STRING)
	private BookingStatus status;
	@Column(name = "created_at")
	private Instant createdAt;
	@Column(name = "expires_at")
	private Instant expiresAt;
	@ElementCollection
	@CollectionTable(name = "booked_seat", joinColumns = @JoinColumn(name = "booking_id"))
	private List<BookedSeatEmbeddable> seats = new ArrayList<>();

	protected BookingEntity() {
	}

	static BookingEntity from(Booking booking) {
		BookingEntity entity = new BookingEntity();
		entity.id = booking.id();
		entity.customerId = booking.customerId();
		entity.showtimeId = booking.showtimeId();
		entity.status = booking.status();
		// timestamptz keeps microseconds; truncate so the saved Booking equals the one read back later.
		entity.createdAt = booking.createdAt().truncatedTo(ChronoUnit.MICROS);
		entity.expiresAt = booking.expiresAt().truncatedTo(ChronoUnit.MICROS);
		// Inserted in seatId order, so overlapping Bookings take seat locks in the same order and don't deadlock.
		entity.seats = booking.seats().stream().sorted(Comparator.comparing(BookedSeat::seatId))
			.map(seat -> new BookedSeatEmbeddable(booking.showtimeId(), seat.seatId(), seat.price()))
			.collect(ArrayList::new, List::add, List::addAll);
		return entity;
	}

	Booking toBooking() {
		return new Booking(id, customerId, showtimeId, status, createdAt, expiresAt,
			seats.stream().map(seat -> new BookedSeat(seat.seatId, seat.price)).toList());
	}

	@Embeddable
	static class BookedSeatEmbeddable {

		/** Copy of the Booking's, so {@code UNIQUE(showtime_id, seat_id)} can see it. */
		@Column(name = "showtime_id")
		private UUID showtimeId;
		@Column(name = "seat_id")
		private UUID seatId;
		private long price;

		protected BookedSeatEmbeddable() {
		}

		BookedSeatEmbeddable(UUID showtimeId, UUID seatId, long price) {
			this.showtimeId = showtimeId;
			this.seatId = seatId;
			this.price = price;
		}
	}
}
