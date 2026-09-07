package ie.budgetTracker.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ie.budgetTracker.api.auth.SessionCookie;
import ie.budgetTracker.api.support.IdempotentRequests;
import ie.budgetTracker.application.auth.AuthService;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.IdempotencyStore;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A signed-in controller slice.
 *
 * `@WebMvcTest` loads the real security chain, so these tests go through it
 * rather than around it - which is the point: a controller that forgot to be
 * protected would show up here rather than in production. The session is
 * granted by stubbing the one thing the filter asks, so nothing about
 * authentication is disabled to make the tests pass.
 *
 * SecurityConfig has to be imported explicitly: `@WebMvcTest` does not pick it
 * up on its own, and without it the slice quietly runs Boot's default chain -
 * which passes or fails for reasons that have nothing to do with this
 * application's rules.
 */
@Import({ ie.budgetTracker.api.auth.SecurityConfig.class, IdempotentRequests.class })
public abstract class WebSliceTest {

	protected static final UUID SIGNED_IN_USER =
			UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

	@MockitoBean
	private AuthService auth;

	/**
	 * Spec §4's idempotency helper is a real bean here, with its store mocked.
	 *
	 * A slice that mocked the helper itself would prove the controller compiles
	 * and nothing about what a retried POST does.
	 */
	@MockitoBean
	protected IdempotencyStore idempotencyStore;

	@MockitoBean
	private CurrentUser currentUser;

	@BeforeEach
	void grantASession() {
		given(auth.authenticate(any())).willReturn(Optional.of(SIGNED_IN_USER));
	}

	/** The cookie a signed-in browser would send. */
	protected static Cookie session() {
		return new Cookie(SessionCookie.NAME, "a-valid-looking-token");
	}
}
