package vn.edu.uet.cinema.business.account;

import java.util.UUID;

public record Account(UUID id, String email, String passwordHash, String fullName, Role role) {
}
