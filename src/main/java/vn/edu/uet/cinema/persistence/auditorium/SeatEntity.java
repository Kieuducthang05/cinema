package vn.edu.uet.cinema.persistence.auditorium;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import vn.edu.uet.cinema.business.auditorium.Seat;
import vn.edu.uet.cinema.business.auditorium.SeatType;

@Entity
@Table(name = "seat")
class SeatEntity {

	@Id
	private UUID id;
	@ManyToOne
	@JoinColumn(name = "auditorium_id", nullable = false)
	private AuditoriumEntity auditorium;
	private String rowLetter;
	private int seatNumber;
	@Enumerated(EnumType.STRING)
	private SeatType type;

	protected SeatEntity() {
	}

	static SeatEntity from(Seat seat, AuditoriumEntity auditorium) {
		SeatEntity entity = new SeatEntity();
		entity.id = seat.id();
		entity.auditorium = auditorium;
		entity.rowLetter = seat.row();
		entity.seatNumber = seat.number();
		entity.type = seat.type();
		return entity;
	}

	Seat toSeat() {
		return new Seat(id, rowLetter, seatNumber, type);
	}
}
