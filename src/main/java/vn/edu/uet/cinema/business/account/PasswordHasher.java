package vn.edu.uet.cinema.business.account;

/** Implemented outside the business layer so it stays free of security libraries. */
public interface PasswordHasher {

	String hash(String rawPassword);

	boolean matches(String rawPassword, String hash);
}
