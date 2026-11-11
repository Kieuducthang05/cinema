package vn.edu.uet.cinema.business.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

	Optional<Account> findById(UUID id);

	Optional<Account> findByEmail(String email);

	boolean existsByEmail(String email);

	/** @throws EmailTakenException if the email is already taken */
	Account save(Account account);
}
