package vn.edu.uet.cinema.persistence.movie;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovieEntityRepository extends JpaRepository<MovieEntity, UUID> {
}
