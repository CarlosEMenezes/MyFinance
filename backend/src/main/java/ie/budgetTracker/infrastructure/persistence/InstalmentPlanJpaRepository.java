package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Owned through the card, which is owned through its account.
 *
 * Three levels, one source of truth: a plan has no owner column that could
 * disagree with the card it belongs to.
 */
interface InstalmentPlanJpaRepository extends JpaRepository<InstalmentPlanEntity, UUID> {

	List<InstalmentPlanEntity> findByCardAccountUserIdOrderByFirstDueDateAsc(UUID userId);
}
