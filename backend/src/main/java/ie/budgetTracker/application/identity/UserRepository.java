package ie.budgetTracker.application.identity;

import ie.budgetTracker.domain.identity.User;
import java.util.Optional;
import java.util.UUID;

/**
 * The port through which users are read and written.
 *
 * Declared here, in the application layer, and implemented in infrastructure:
 * that is what keeps the dependency arrow pointing inward. The service knows
 * there is a store; it does not know it is JPA.
 */
public interface UserRepository {

	Optional<User> findById(UUID id);

	User save(User user);
}
