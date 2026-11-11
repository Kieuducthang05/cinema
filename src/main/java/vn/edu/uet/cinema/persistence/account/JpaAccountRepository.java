package vn.edu.uet.cinema.persistence.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import vn.edu.uet.cinema.business.account.Account;
import vn.edu.uet.cinema.business.account.AccountRepository;
import vn.edu.uet.cinema.business.account.EmailTakenException;

@Repository
class JpaAccountRepository implements AccountRepository {

	private final AccountEntityRepository jpa;

	JpaAccountRepository(AccountEntityRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<Account> findById(UUID id) {
		return jpa.findById(id).map(AccountEntity::toAccount);
	}

	@Override
	public Optional<Account> findByEmail(String email) {
		return jpa.findByEmail(email).map(AccountEntity::toAccount);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpa.existsByEmail(email);
	}

	@Override
	public Account save(Account account) {
		try {
			return jpa.saveAndFlush(AccountEntity.from(account)).toAccount();
		} catch (DataIntegrityViolationException e) {
			// Two registrations with the same email raced past existsByEmail; the unique constraint decides.
			throw new EmailTakenException();
		}
	}
}
