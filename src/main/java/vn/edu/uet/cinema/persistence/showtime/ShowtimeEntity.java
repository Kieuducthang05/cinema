package vn.edu.uet.cinema.persistence.showtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.edu.uet.cinema.business.showtime.Cancellation;
import vn.edu.uet.cinema.business.showtime.CancellationReason;
import vn.edu.uet.cinema.business.showtime.Showtime;
import vn.edu.uet.cinema.business.showtime.ShowtimeStatus;

@Entity
@Table(name = "showtime")
class ShowtimeEntity {

	@Id
	private UUID id;
	@Column(name = "movie_id")
	private UUID movieId;
	@Column(name = "auditorium_id")
	private UUID auditoriumId;
	@Column(name = "starts_at")
	private Instant startsAt;
	@Column(name = "ends_at")
	private Instant endsAt;
	@Column(name = "base_price")
	private long basePrice;
	@Enumerated(EnumType.STRING)
	private ShowtimeStatus status;
	@Enumerated(EnumType.STRING)
	@Column(name = "cancellation_reason")
	private CancellationReason cancellationReason;
	@Column(name = "cancellation_note")
	private String cancellationNote;

	protected ShowtimeEntity() {
	}

	static ShowtimeEntity from(Showtime showtime) {
		ShowtimeEntity entity = new ShowtimeEntity();
		entity.id = showtime.id();
		entity.movieId = showtime.movieId();
		entity.auditoriumId = showtime.auditoriumId();
		entity.startsAt = showtime.startsAt();
		entity.endsAt = showtime.endsAt();
		entity.basePrice = showtime.basePrice();
		entity.status = showtime.status();
		if (showtime.cancellation() != null) {
			entity.cancellationReason = showtime.cancellation().reason();
			entity.cancellationNote = showtime.cancellation().note();
		}
		return entity;
	}

	Showtime toShowtime() {
		Cancellation cancellation = cancellationReason == null ? null
			: new Cancellation(cancellationReason, cancellationNote);
		return new Showtime(id, movieId, auditoriumId, startsAt, endsAt, basePrice, status, cancellation);
	}
}
