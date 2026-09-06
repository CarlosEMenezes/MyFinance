package ie.budgetTracker.infrastructure.auth;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Whose figures these are: the principal the session cookie resolved to.
 *
 * This replaces the single-user placeholder, and nothing else changed to make
 * that happen. Every service already asked the question through this port, so
 * adding authentication was a matter of answering it differently rather than
 * threading a user id through the application layer after the fact.
 */
@Component
class AuthenticatedUser implements CurrentUser {

	@Override
	public UUID id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
			// The filter chain should have refused the request long before here, so
			// this is a wiring mistake rather than a signed-out user.
			throw AppException.unauthorised("Not signed in");
		}
		return userId;
	}
}
