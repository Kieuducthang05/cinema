package vn.edu.uet.cinema.business.account;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link AccountRepository} for business tests. */
public class InMemoryAccounts implements AccountRepository {

	private final Map<UUID, Account> rows = new HashMap<>();

	public int size() {
		return rows.size();
	}

	@Override
	public Optional<Account> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Optional<Account> findByEmail(String email) {
		return rows.values().stream().filter(a -> a.email().equals(email)).findFirst();
	}

	@Override
	public boolean existsByEmail(String email) {
		return findByEmail(email).isPresent();
	}

	@Override
	public Account save(Account account) {
		rows.put(account.id(), account);
		return account;
	}
}
