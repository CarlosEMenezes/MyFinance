package ie.budgetTracker.api.auth;

import ie.budgetTracker.application.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns the session cookie into an authenticated principal.
 *
 * The principal is the user's UUID and nothing else. There are no roles and no
 * authorities, because there is nothing in this application one signed-in
 * person may do that another may not — the only question is ever *whose* data
 * this is, and that is answered by the user id, not by a permission.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private final AuthService auth;

	public SessionAuthenticationFilter(AuthService auth) {
		this.auth = auth;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response, @NonNull FilterChain chain)
			throws ServletException, IOException {

		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			tokenFrom(request)
					.flatMap(auth::authenticate)
					.ifPresent(SessionAuthenticationFilter::authenticateAs);
		}
		chain.doFilter(request, response);
	}

	private static void authenticateAs(UUID userId) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of()));
	}

	private static Optional<String> tokenFrom(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
				.filter(cookie -> SessionCookie.NAME.equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> !value.isBlank())
				.findFirst();
	}
}
