package ie.budgetTracker.api.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Everything requires a session except registering and signing in (ADR-11).
 *
 * The default is *deny*: `anyRequest().authenticated()` means a new endpoint is
 * protected the moment it exists, and someone has to write a line to open it.
 * An allow-list default would mean forgetting a line leaves data public, and
 * those two mistakes are not equally bad.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, SessionAuthenticationFilter sessions)
			throws Exception {

		return http
				// The session is a cookie this application manages itself, so the
				// servlet container must not also keep one.
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// SameSite=Strict on the session cookie is what stops a cross-site
				// request carrying it, so Spring's token-based CSRF protection would
				// add a second mechanism for a risk already closed - at the cost of
				// a token the SPA would have to fetch and echo.
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
						.anyRequest().authenticated())
				// 401 rather than a redirect to a login page: this serves an API, and
				// the frontend decides what to show.
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.addFilterBefore(sessions, UsernamePasswordAuthenticationFilter.class)
				.cors(Customizer.withDefaults())
				.build();
	}

	/**
	 * BCrypt today, Argon2id at spec §6.2, with no forced reset in between.
	 *
	 * `DelegatingPasswordEncoder` writes the algorithm into the hash, so changing
	 * the default later re-hashes each password on that user's next successful
	 * sign-in. That migration path is the reason to use it from the first commit
	 * rather than a bare BCryptPasswordEncoder.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
