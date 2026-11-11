package vn.edu.uet.cinema.persistence.cinema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import vn.edu.uet.cinema.business.cinema.Cinema;

@Entity
@Table(name = "cinema")
class CinemaEntity {

	@Id
	private UUID id;
	private String name;
	@Column(columnDefinition = "text")
	private String address;

	protected CinemaEntity() {
	}

	static CinemaEntity from(Cinema cinema) {
		CinemaEntity entity = new CinemaEntity();
		entity.id = cinema.id();
		entity.name = cinema.name();
		entity.address = cinema.address();
		return entity;
	}

	Cinema toCinema() {
		return new Cinema(id, name, address);
	}
}
