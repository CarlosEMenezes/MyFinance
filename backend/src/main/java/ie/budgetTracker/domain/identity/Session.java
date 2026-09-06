package ie.budgetTracker.domain.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A signed-in session (ADR-11).
 *
 * The token itself is not here: only its hash is ever stored, and the plain
 * value exists for exactly as long as it takes to put it in a cookie. A type
 * that could hold the live token would eventually end up in a log.
 */
public record Session(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt,
		Instant lastSeenAt) {

	public boolean hasExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}
}
