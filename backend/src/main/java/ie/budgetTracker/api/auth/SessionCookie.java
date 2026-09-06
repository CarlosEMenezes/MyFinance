package ie.budgetTracker.api.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The cookie the session token travels in (ADR-11).
 *
 * HttpOnly, so no script on the page can read it — which is the whole reason
 * the frontend has no token handling and `lib/http.ts` needs no Authorization
 * header. SameSite=Strict, so the browser does not attach it to cross-site
 * requests, which is what makes CSRF a non-issue for this API.
 */
@Component
public class SessionCookie {

	public static final String NAME = "bt_session";

	private final boolean secure;

	SessionCookie(@Value("${app.cookie.secure:true}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie issue(String token, Instant expiresAt) {
		return base(token)
				.maxAge(Duration.between(Instant.now(), expiresAt))
				.build();
	}

	/**
	 * The same cookie with no value and no lifetime.
	 *
	 * Every attribute must match the one that was issued or the browser treats
	 * it as a different cookie and keeps the original.
	 */
	public ResponseCookie clear() {
		return base("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Strict")
				// Not sent with static assets, only with the API.
				.path("/api");
	}
}
