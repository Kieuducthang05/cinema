package vn.edu.uet.cinema.api.account;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import vn.edu.uet.cinema.business.account.Account;
import vn.edu.uet.cinema.config.SecurityConfig;

@Component
class TokenIssuer {

	static final Duration TTL = Duration.ofHours(1);

	private final JwtEncoder encoder;

	TokenIssuer(JwtEncoder encoder) {
		this.encoder = encoder;
	}

	String issue(Account account) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(account.id().toString())
			.issuedAt(now)
			.expiresAt(now.plus(TTL))
			.claim(SecurityConfig.ROLES_CLAIM, List.of(account.role().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
