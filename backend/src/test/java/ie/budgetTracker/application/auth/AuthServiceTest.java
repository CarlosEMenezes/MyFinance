package ie.budgetTracker.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.auth.dto.LoginRequest;
import ie.budgetTracker.application.auth.dto.RegisterRequest;
import ie.budgetTracker.application.auth.dto.SignedInSession;
import ie.budgetTracker.domain.identity.Credentials;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.Session;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Registering, signing in and signing out (ADR-11). */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-06T10:00:00Z");
	private static final UUID ADA = UUID.randomUUID();

	@Mock
	private CredentialsRepository credentials;

	@Mock
	private SessionRepository sessions;

	/** The real encoder: what is under test includes that a hash verifies. */
	private final PasswordEncoder passwordEncoder =
			PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private final SessionTokens tokens = new SessionTokens();

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(credentials, sessions, passwordEncoder, tokens,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static User ada() {
		return new User(ADA, "Ada Lovelace", null, null, null, PayCycle.IRREGULAR, Currency.EUR,
				DateFormatPreference.DD_MM_YYYY, WeekStart.MONDAY,
				new UserPreferences(true, true, false));
	}

	@Nested
	@DisplayName("registering")
	class Registering {

		@Test
		void createsAnAccountAndSignsThePersonStraightIn() {
			given(credentials.emailIsTaken("ada@example.com")).willReturn(false);
			given(credentials.register(any(), any(), any())).willReturn(ada());

			SignedInSession session = service.register(
					new RegisterRequest("ada@example.com", "a-long-enough-passphrase", "Ada Lovelace"));

			// Registering and then being asked to sign in is a step that exists only
			// because the implementation found it convenient.
			assertThat(session.token()).isNotBlank();
			assertThat(session.user().name()).isEqualTo("Ada Lovelace");
		}

		@Test
		void storesAHashRatherThanThePassword() {
			given(credentials.emailIsTaken(any())).willReturn(false);
			given(credentials.register(any(), any(), any())).willReturn(ada());

			service.register(new RegisterRequest("ada@example.com", "a-long-enough-passphrase",
					"Ada Lovelace"));

			ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
			org.mockito.Mockito.verify(credentials).register(any(), hash.capture(), any());

			assertThat(hash.getValue()).doesNotContain("a-long-enough-passphrase");
			// The algorithm is written into the hash, which is what makes the move
			// to Argon2id at spec §6.2 a change of default rather than a reset.
			assertThat(hash.getValue()).startsWith("{bcrypt}");
			assertThat(passwordEncoder.matches("a-long-enough-passphrase", hash.getValue())).isTrue();
		}

		@Test
		void lowerCasesTheEmailSoOneAddressIsOnePerson() {
			given(credentials.emailIsTaken("ada@example.com")).willReturn(false);
			given(credentials.register(any(), any(), any())).willReturn(ada());

			service.register(new RegisterRequest("  Ada@Example.COM  ",
					"a-long-enough-passphrase", "Ada"));

			ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
			org.mockito.Mockito.verify(credentials).register(email.capture(), any(), any());
			assertThat(email.getValue()).isEqualTo("ada@example.com");
		}

		@Test
		void refusesAnEmailThatIsAlreadyRegistered() {
			given(credentials.emailIsTaken("ada@example.com")).willReturn(true);

			assertThatThrownBy(() -> service.register(new RegisterRequest("ada@example.com",
					"a-long-enough-passphrase", "Ada")))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("already registered");
		}

		@Test
		void startsWithAnIrregularPayCycleBecauseThatIsWhatThisAppIsFor() {
			given(credentials.emailIsTaken(any())).willReturn(false);
			given(credentials.register(any(), any(), any())).willReturn(ada());

			service.register(new RegisterRequest("ada@example.com", "a-long-enough-passphrase",
					"Ada"));

			ArgumentCaptor<User> profile = ArgumentCaptor.forClass(User.class);
			org.mockito.Mockito.verify(credentials).register(any(), any(), profile.capture());
			assertThat(profile.getValue().payCycle()).isEqualTo(PayCycle.IRREGULAR);
		}
	}

	@Nested
	@DisplayName("signing in")
	class SigningIn {

		private void adaIsRegisteredWith(String password) {
			given(credentials.findByEmail("ada@example.com")).willReturn(
					Optional.of(new CredentialsRepository.StoredCredentials(ADA,
							new Credentials("ada@example.com", passwordEncoder.encode(password)))));
		}

		@Test
		void issuesASessionForTheRightPassword() {
			adaIsRegisteredWith("a-long-enough-passphrase");
			given(credentials.findProfile(ADA)).willReturn(Optional.of(ada()));

			SignedInSession session =
					service.login(new LoginRequest("ada@example.com", "a-long-enough-passphrase"));

			assertThat(session.token()).isNotBlank();
			assertThat(session.expiresAt()).isEqualTo(NOW.plus(AuthService.SESSION_LIFETIME));
		}

		@Test
		void refusesAWrongPassword() {
			adaIsRegisteredWith("a-long-enough-passphrase");

			assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "wrong")))
					.isInstanceOf(AppException.class);
		}

		@Test
		void saysTheSameThingWhetherOrNotTheEmailExists() {
			// "No account with that email" would tell whoever asked that an address
			// is not registered, which is information about a person they did not
			// have. Both failures answer identically.
			adaIsRegisteredWith("a-long-enough-passphrase");
			given(credentials.findByEmail("nobody@example.com")).willReturn(Optional.empty());

			String unknownEmail = failureMessageFor("nobody@example.com", "whatever-it-is");
			String wrongPassword = failureMessageFor("ada@example.com", "wrong-passphrase");

			assertThat(unknownEmail).isEqualTo(wrongPassword);
		}

		private String failureMessageFor(String email, String password) {
			try {
				service.login(new LoginRequest(email, password));
				throw new AssertionError("expected the sign-in to be refused");
			} catch (AppException refused) {
				return refused.getMessage();
			}
		}

		@Test
		void storesOnlyTheHashOfTheTokenItHandsOut() {
			adaIsRegisteredWith("a-long-enough-passphrase");
			given(credentials.findProfile(ADA)).willReturn(Optional.of(ada()));

			SignedInSession session =
					service.login(new LoginRequest("ada@example.com", "a-long-enough-passphrase"));

			ArgumentCaptor<Session> stored = ArgumentCaptor.forClass(Session.class);
			org.mockito.Mockito.verify(sessions).save(stored.capture());

			// A leaked backup then contains no usable session (ADR-11).
			assertThat(stored.getValue().tokenHash()).isNotEqualTo(session.token());
			assertThat(stored.getValue().tokenHash()).isEqualTo(tokens.hash(session.token()));
		}
	}

	@Nested
	@DisplayName("using and ending a session")
	class UsingASession {

		@Test
		void resolvesALiveTokenToItsOwner() {
			given(sessions.findByTokenHash(tokens.hash("a-token"))).willReturn(
					Optional.of(new Session(tokens.hash("a-token"), ADA, NOW, NOW.plusSeconds(60),
							NOW)));

			assertThat(service.authenticate("a-token")).contains(ADA);
		}

		@Test
		void pushesTheExpiryOutWhenTheSessionIsUsed() {
			given(sessions.findByTokenHash(any())).willReturn(
					Optional.of(new Session(tokens.hash("a-token"), ADA, NOW, NOW.plusSeconds(60),
							NOW)));

			service.authenticate("a-token");

			ArgumentCaptor<Session> refreshed = ArgumentCaptor.forClass(Session.class);
			org.mockito.Mockito.verify(sessions).save(refreshed.capture());
			// Using the app keeps you signed in; leaving it does not.
			assertThat(refreshed.getValue().expiresAt())
					.isEqualTo(NOW.plus(AuthService.SESSION_LIFETIME));
		}

		@Test
		void refusesAnExpiredSessionAndDeletesIt() {
			String hash = tokens.hash("a-token");
			given(sessions.findByTokenHash(hash)).willReturn(
					Optional.of(new Session(hash, ADA, NOW.minusSeconds(120), NOW.minusSeconds(60),
							NOW.minusSeconds(90))));

			assertThat(service.authenticate("a-token")).isEmpty();
			// Not left to accumulate into a list of everywhere the user has been.
			org.mockito.Mockito.verify(sessions).deleteByTokenHash(hash);
		}

		@Test
		void refusesATokenThatWasNeverIssued() {
			given(sessions.findByTokenHash(any())).willReturn(Optional.empty());

			assertThat(service.authenticate("made-up")).isEmpty();
		}

		@Test
		void endsOneSessionOnSignOut() {
			service.logout("a-token");

			org.mockito.Mockito.verify(sessions).deleteByTokenHash(tokens.hash("a-token"));
		}

		@Test
		void endsEverySessionWhenSigningOutEverywhere() {
			service.logoutEverywhere(ADA);

			org.mockito.Mockito.verify(sessions).deleteAllForUser(ADA);
		}
	}

	@Nested
	@DisplayName("the tokens themselves")
	class Tokens {

		@Test
		void areDifferentEveryTime() {
			assertThat(tokens.generate()).isNotEqualTo(tokens.generate());
		}

		@Test
		void areLongEnoughToBeUnguessable() {
			// 32 bytes, base64url without padding.
			assertThat(tokens.generate()).hasSize(43);
		}

		@Test
		void hashToSomethingThatCannotBeReplayed() {
			String token = tokens.generate();

			assertThat(tokens.hash(token)).hasSize(64).isNotEqualTo(token);
			assertThat(tokens.hash(token)).isEqualTo(tokens.hash(token));
		}
	}
}
