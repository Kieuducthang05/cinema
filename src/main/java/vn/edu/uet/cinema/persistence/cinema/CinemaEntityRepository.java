package vn.edu.uet.cinema.persistence.cinema;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CinemaEntityRepository extends JpaRepository<CinemaEntity, UUID> {
}
