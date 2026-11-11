package vn.edu.uet.cinema.persistence.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import vn.edu.uet.cinema.business.account.Account;
import vn.edu.uet.cinema.business.account.Role;

@Entity
@Table(name = "account")
class AccountEntity {

	@Id
	private UUID id;
	private String email;
	private String passwordHash;
	private String fullName;
	@Enumerated(EnumType.STRING)
	private Role role;

	protected AccountEntity() {
	}

	static AccountEntity from(Account account) {
		AccountEntity entity = new AccountEntity();
		entity.id = account.id();
		entity.email = account.email();
		entity.passwordHash = account.passwordHash();
		entity.fullName = account.fullName();
		entity.role = account.role();
		return entity;
	}

	Account toAccount() {
		return new Account(id, email, passwordHash, fullName, role);
	}
}
