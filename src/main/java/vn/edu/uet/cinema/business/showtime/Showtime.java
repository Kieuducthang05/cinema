package vn.edu.uet.cinema.business.showtime;

import java.time.Instant;
import java.util.UUID;

/** {@code cancellation} is null unless the Showtime is Cancelled. */
public record Showtime(UUID id, UUID movieId, UUID auditoriumId, Instant startsAt, Instant endsAt, long basePrice,
	ShowtimeStatus status, Cancellation cancellation) {

	/** A new, Scheduled Showtime. */
	public Showtime(UUID id, UUID movieId, UUID auditoriumId, Instant startsAt, Instant endsAt, long basePrice) {
		this(id, movieId, auditoriumId, startsAt, endsAt, basePrice, ShowtimeStatus.SCHEDULED, null);
	}
}
