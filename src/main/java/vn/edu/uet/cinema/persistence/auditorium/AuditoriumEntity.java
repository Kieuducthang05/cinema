package vn.edu.uet.cinema.persistence.auditorium;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.edu.uet.cinema.business.auditorium.Auditorium;
import vn.edu.uet.cinema.business.auditorium.Seat;

@Entity
@Table(name = "auditorium")
class AuditoriumEntity {

	@Id
	private UUID id;
	private UUID cinemaId;
	private String name;
	private int rows;
	private int seatsPerRow;
	@OneToMany(mappedBy = "auditorium", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("rowLetter ASC, seatNumber ASC")
	private List<SeatEntity> seats = new ArrayList<>();

	protected AuditoriumEntity() {
	}

	static AuditoriumEntity from(Auditorium auditorium) {
		AuditoriumEntity entity = new AuditoriumEntity();
		entity.id = auditorium.id();
		entity.cinemaId = auditorium.cinemaId();
		entity.name = auditorium.name();
		entity.rows = auditorium.rows();
		entity.seatsPerRow = auditorium.seatsPerRow();
		entity.seats = auditorium.seats().stream().map(seat -> SeatEntity.from(seat, entity)).collect(ArrayList::new,
			List::add, List::addAll);
		return entity;
	}

	Auditorium toAuditorium() {
		List<Seat> mappedSeats = seats.stream().map(SeatEntity::toSeat).toList();
		return new Auditorium(id, cinemaId, name, rows, seatsPerRow, mappedSeats);
	}
}
