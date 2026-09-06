package ie.budgetTracker.api.auth;

import ie.budgetTracker.application.auth.AuthService;
import ie.budgetTracker.application.auth.dto.LoginRequest;
import ie.budgetTracker.application.auth.dto.RegisterRequest;
import ie.budgetTracker.application.auth.dto.SignedInSession;
import ie.budgetTracker.application.identity.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registering, signing in and signing out.
 *
 * The session token is put into a cookie and never into a body. That is the
 * whole of ADR-11's browser story: the frontend receives a profile, the browser
 * receives a cookie it cannot read, and no JavaScript anywhere handles a
 * credential.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService auth;
	private final SessionCookie cookie;

	AuthController(AuthService auth, SessionCookie cookie) {
		this.auth = auth;
		this.cookie = cookie;
	}

	@PostMapping("/register")
	ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		return signedIn(auth.register(request), HttpStatus.CREATED);
	}

	@PostMapping("/login")
	ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
		return signedIn(auth.login(request), HttpStatus.OK);
	}

	/**
	 * Ends this session, and answers the same either way.
	 *
	 * Signing out with no session is not an error to report: the caller wanted
	 * to be signed out, and they are.
	 */
	@PostMapping("/logout")
	ResponseEntity<Void> logout(
			@CookieValue(name = SessionCookie.NAME, required = false) String token) {

		if (token != null && !token.isBlank()) {
			auth.logout(token);
		}
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
				.build();
	}

	private ResponseEntity<UserResponse> signedIn(SignedInSession session, HttpStatus status) {
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE,
						cookie.issue(session.token(), session.expiresAt()).toString())
				.body(session.user());
	}
}
