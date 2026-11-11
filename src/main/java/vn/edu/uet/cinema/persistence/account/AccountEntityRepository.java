package vn.edu.uet.cinema.persistence.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountEntityRepository extends JpaRepository<AccountEntity, UUID> {

	Optional<AccountEntity> findByEmail(String email);

	boolean existsByEmail(String email);
}
