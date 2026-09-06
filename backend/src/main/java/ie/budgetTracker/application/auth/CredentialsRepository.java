package ie.budgetTracker.application.auth;

import ie.budgetTracker.domain.identity.Credentials;
import ie.budgetTracker.domain.identity.User;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration and the credential lookup a sign-in needs.
 *
 * Deliberately separate from `UserRepository`: reading a profile is a routine
 * operation and reading a password hash is not, and keeping them on different
 * ports means no ordinary code path has a credential within reach.
 */
public interface CredentialsRepository {

	boolean emailIsTaken(String normalisedEmail);

	/** Creates the user and their credential together, or neither. */
	User register(String normalisedEmail, String passwordHash, User profile);

	Optional<StoredCredentials> findByEmail(String normalisedEmail);

	/** The profile behind a credential, once the password has been checked. */
	Optional<User> findProfile(UUID userId);

	record StoredCredentials(UUID userId, Credentials credentials) {
	}
}
