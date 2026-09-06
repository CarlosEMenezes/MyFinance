package ie.budgetTracker.application.auth;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.auth.dto.LoginRequest;
import ie.budgetTracker.application.auth.dto.RegisterRequest;
import ie.budgetTracker.application.auth.dto.SignedInSession;
import ie.budgetTracker.application.identity.dto.UserResponse;
import ie.budgetTracker.domain.identity.Credentials;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.Session;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering, signing in, and signing out (ADR-11).
 *
 * The session token is opaque and its hash is what gets stored, so this class
 * is the only place a live token ever exists — for as long as it takes to
 * return it to the controller, which puts it straight into an HttpOnly cookie.
 */
@Service
public class AuthService {

	/**
	 * How long a session lasts without being used.
	 *
	 * Long enough that a person is not signed out mid-month while planning, short
	 * enough that a forgotten session on a borrowed machine expires. Every
	 * authenticated request pushes it out again, so this is an idle timeout
	 * rather than a hard one.
	 */
	static final Duration SESSION_LIFETIME = Duration.ofDays(14);

	private final CredentialsRepository credentials;
	private final SessionRepository sessions;
	private final PasswordEncoder passwordEncoder;
	private final SessionTokens tokens;
	private final Clock clock;

	public AuthService(CredentialsRepository credentials, SessionRepository sessions,
			PasswordEncoder passwordEncoder, SessionTokens tokens, Clock clock) {
		this.credentials = credentials;
		this.sessions = sessions;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.clock = clock;
	}

	@Transactional
	public SignedInSession register(RegisterRequest request) {
		String email = Credentials.normaliseEmail(request.email());
		if (credentials.emailIsTaken(email)) {
			throw AppException.conflict("That email address is already registered");
		}

		User created = credentials.register(email, passwordEncoder.encode(request.password()),
				newProfile(request.name()));

		return startSession(created);
	}

	/**
	 * Signs in, or refuses without saying which half was wrong.
	 *
	 * "No account with that email" tells whoever asked that the address is not
	 * registered, which is information about a person they did not have. Both
	 * failures answer identically.
	 *
	 * The password is verified even when the email is unknown, against a hash
	 * that cannot match. Skipping it would return faster for an unknown address
	 * than for a known one, and that difference is readable.
	 */
	@Transactional
	public SignedInSession login(LoginRequest request) {
		Optional<CredentialsRepository.StoredCredentials> stored =
				credentials.findByEmail(Credentials.normaliseEmail(request.email()));

		boolean matches = passwordEncoder.matches(request.password(),
				stored.map(found -> found.credentials().passwordHash()).orElse(NO_SUCH_USER_HASH));

		if (stored.isEmpty() || !matches) {
			throw AppException.unauthorised("That email address and password do not match");
		}

		return startSession(profileFor(stored.get().userId()));
	}

	@Transactional
	public void logout(String token) {
		sessions.deleteByTokenHash(tokens.hash(token));
	}

	/** What a password change must do, and what "sign out everywhere" is. */
	@Transactional
	public void logoutEverywhere(UUID userId) {
		sessions.deleteAllForUser(userId);
	}

	/**
	 * Resolves a token to its owner, or to nobody.
	 *
	 * An expired session is deleted rather than left to accumulate, so the table
	 * does not become a list of everywhere the user has ever signed in.
	 */
	@Transactional
	public Optional<UUID> authenticate(String token) {
		String hash = tokens.hash(token);
		Optional<Session> session = sessions.findByTokenHash(hash);

		if (session.isEmpty()) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		if (session.get().hasExpired(now)) {
			sessions.deleteByTokenHash(hash);
			return Optional.empty();
		}

		// Sliding expiry: using the app keeps you signed in, leaving it does not.
		sessions.save(new Session(hash, session.get().userId(), session.get().createdAt(),
				now.plus(SESSION_LIFETIME), now));

		return Optional.of(session.get().userId());
	}

	private SignedInSession startSession(User user) {
		String token = tokens.generate();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(SESSION_LIFETIME);

		sessions.save(new Session(tokens.hash(token), user.id(), now, expiresAt, now));

		return new SignedInSession(token, expiresAt, UserResponse.from(user));
	}

	private User profileFor(UUID userId) {
		return credentials.findProfile(userId)
				.orElseThrow(() -> AppException.notFound("No profile for that account"));
	}

	/**
	 * The defaults a new account starts with.
	 *
	 * IRREGULAR because that is what this application is for: it is the normal
	 * case here, not the exception. Everything else is editable on Settings, and
	 * asking for it at registration would be a form standing between someone and
	 * the thing they came to do.
	 */
	private static User newProfile(String name) {
		return new User(null, name.trim(), null, null, null, PayCycle.IRREGULAR, Currency.EUR,
				DateFormatPreference.DD_MM_YYYY, WeekStart.MONDAY,
				new UserPreferences(true, true, false));
	}

	/**
	 * A real BCrypt hash of a value nothing will ever submit.
	 *
	 * Verified against when the email is unknown, so that a failed sign-in takes
	 * the same time whether or not the address exists. A cheaper placeholder
	 * would return early and leak that difference in the timing.
	 */
	private static final String NO_SUCH_USER_HASH =
			"{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
