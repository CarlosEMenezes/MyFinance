package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.goals.GoalRepository;
import ie.budgetTracker.domain.goals.Goal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link GoalRepository}. */
@Repository
class JpaGoalRepository implements GoalRepository {

	private final GoalJpaRepository goals;
	private final UserJpaRepository users;

	JpaGoalRepository(GoalJpaRepository goals, UserJpaRepository users) {
		this.goals = goals;
		this.users = users;
	}

	@Override
	public List<Goal> findAllForUser(UUID userId) {
		return goals.findByUserIdOrderByPriorityRankAsc(userId).stream()
				.map(GoalEntity::toDomain)
				.toList();
	}

	@Override
	public Goal create(UUID userId, Goal goal) {
		UserEntity owner = users.findById(userId)
				.orElseThrow(() -> AppException.notFound("No user with id " + userId));

		return goals.save(new GoalEntity(owner, goal)).toDomain();
	}
}
