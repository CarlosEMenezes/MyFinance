package ie.budgetTracker.application.goals;

import ie.budgetTracker.domain.goals.Goal;
import java.util.List;
import java.util.UUID;

/**
 * The port through which goals are read and written.
 *
 * There is no method here that writes {@code savedAmount} on its own. BR-11
 * has one source of truth for what has been saved, and an "allocate to goal"
 * call would quietly become a second (BR-18).
 */
public interface GoalRepository {

	/** Ranked, because BR-11 says goals are ranked and a list has to show that. */
	List<Goal> findAllForUser(UUID userId);

	Goal create(UUID userId, Goal goal);
}
