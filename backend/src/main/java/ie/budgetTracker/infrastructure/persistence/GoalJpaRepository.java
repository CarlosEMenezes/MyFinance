package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ranked, because BR-11 ranks goals and a list has to show that order. */
interface GoalJpaRepository extends JpaRepository<GoalEntity, UUID> {

	List<GoalEntity> findByUserIdOrderByPriorityRankAsc(UUID userId);
}
