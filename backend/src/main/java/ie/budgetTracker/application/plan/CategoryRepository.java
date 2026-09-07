package ie.budgetTracker.application.plan;

import ie.budgetTracker.domain.plan.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The port through which categories and their plans are read and written.
 *
 * Every method takes the user whose plan is being asked for, and there is no
 * overload that does not (ADR-11).
 */
public interface CategoryRepository {

	List<Category> findAllForUser(UUID userId);

	Optional<Category> findForUser(UUID userId, UUID categoryId);

	Category create(UUID userId, Category category);

	/**
	 * Saves an edited category, matched on owner and id together.
	 *
	 * Another user's id is not found rather than forbidden: a 403 would confirm
	 * the id exists (ADR-11).
	 */
	Category update(UUID userId, Category category);
}
