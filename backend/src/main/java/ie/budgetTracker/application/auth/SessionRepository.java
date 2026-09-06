package ie.budgetTracker.application.auth;

import ie.budgetTracker.domain.identity.Session;
import java.util.Optional;
import java.util.UUID;

/** Where sessions live (ADR-11). */
public interface SessionRepository {

	Session save(Session session);

	Optional<Session> findByTokenHash(String tokenHash);

	void deleteByTokenHash(String tokenHash);

	/** "Sign out everywhere", and what a password change must do. */
	void deleteAllForUser(UUID userId);
}
