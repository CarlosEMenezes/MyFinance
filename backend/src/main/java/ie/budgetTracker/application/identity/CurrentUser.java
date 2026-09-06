package ie.budgetTracker.application.identity;

import java.util.UUID;

/**
 * Whose figures are being asked for.
 *
 * A port, not a lookup, because the answer will come from an authenticated
 * principal once spec §6 step 2's auth is built. Until then it resolves to the
 * single seeded account, and every service already asks the question the right
 * way — so adding auth means replacing one adapter rather than threading a
 * user id through every call site.
 */
public interface CurrentUser {

	UUID id();
}
