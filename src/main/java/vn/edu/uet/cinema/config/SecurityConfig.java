package vn.edu.uet.cinema.config;

import static org.springframework.http.HttpMethod.GET;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.edu.uet.cinema.business.account.PasswordHasher;

/**
 * The only place that decides who may call what. Controllers never check authentication themselves:
 * Spring Security's filter chain validates the bearer JWT and applies these rules before any controller runs.
 */
@Configuration
public class SecurityConfig {

	/** Token claim holding the Account's roles, mapped to {@code ROLE_*} authorities. */
	public static final String ROLES_CLAIM = "roles";

	private static final String[] CATALOGUE = {"/api/cinemas/**", "/api/auditoriums/**", "/api/movies/**",
		"/api/showtimes/**"};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		AuthenticationEntryPoint unauthorized = problemJson(new BearerTokenAuthenticationEntryPoint());
		AccessDeniedHandler forbidden = problemJson(new BearerTokenAccessDeniedHandler());
		return http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**", "/error", "/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
					"/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
				.requestMatchers(GET, CATALOGUE).permitAll()
				.requestMatchers(CATALOGUE).hasRole("ADMIN")
				.requestMatchers("/api/bookings/**").hasRole("CUSTOMER")
				.requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesClaimConverter()))
				.authenticationEntryPoint(unauthorized)
				.accessDeniedHandler(forbidden))
			.build();
	}

	@Bean
	SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey) {
		return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	PasswordHasher passwordHasher() {
		BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
		return new PasswordHasher() {
			@Override
			public String hash(String rawPassword) {
				return bcrypt.encode(rawPassword);
			}

			@Override
			public boolean matches(String rawPassword, String hash) {
				return bcrypt.matches(rawPassword, hash);
			}
		};
	}

	/** Lets the bearer defaults set the status and WWW-Authenticate header, then adds an RFC 9457 body. */
	private static AuthenticationEntryPoint problemJson(AuthenticationEntryPoint delegate) {
		return (request, response, e) -> {
			delegate.commence(request, response, e);
			writeProblem(response);
		};
	}

	private static AccessDeniedHandler problemJson(AccessDeniedHandler delegate) {
		return (request, response, e) -> {
			delegate.handle(request, response, e);
			writeProblem(response);
		};
	}

	private static void writeProblem(HttpServletResponse response) throws IOException {
		HttpStatus status = HttpStatus.valueOf(response.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d}"
			.formatted(status.getReasonPhrase(), status.value()));
	}

	/** Maps the token's roles claim to {@code ROLE_*} authorities used by {@code hasRole}. */
	private static JwtAuthenticationConverter rolesClaimConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
