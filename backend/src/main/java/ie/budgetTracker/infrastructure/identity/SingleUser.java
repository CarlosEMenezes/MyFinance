package ie.budgetTracker.infrastructure.identity;

import ie.budgetTracker.application.identity.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the current user while there is only one.
 *
 * Spec §6 step 2 pairs identity with auth, and auth is not built: the
 * frontend's ten pages include no sign-in screen, so there is nothing to
 * authenticate against yet, and §6.2 specifies the real thing (Argon2id, TOTP,
 * recovery codes) properly.
 *
 * What matters is that every service already asks "who is this?" through a
 * port. When auth arrives it replaces this one class; no call site changes,
 * and no user id has to be threaded through the application layer after the
 * fact.
 */
@Component
class SingleUser implements CurrentUser {

	/** Matches the row seeded by V3__seed_single_user.sql. */
	private static final UUID ONLY_USER = UUID.fromString("00000000-0000-4000-8000-000000000001");

	@Override
	public UUID id() {
		return ONLY_USER;
	}
}
