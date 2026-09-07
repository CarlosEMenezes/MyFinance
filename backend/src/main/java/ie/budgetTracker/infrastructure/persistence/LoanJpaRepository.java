package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Owned through the account the principal landed in (BR-2). */
interface LoanJpaRepository extends JpaRepository<LoanEntity, UUID> {

	List<LoanEntity> findByDepositAccountUserIdOrderByFirstDueDateAsc(UUID userId);
}
