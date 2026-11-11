package vn.edu.uet.cinema.persistence.auditorium;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditoriumEntityRepository extends JpaRepository<AuditoriumEntity, UUID> {

	List<AuditoriumEntity> findByCinemaId(UUID cinemaId);

	boolean existsByCinemaId(UUID cinemaId);

	boolean existsByCinemaIdAndName(UUID cinemaId, String name);
}
