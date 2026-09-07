package ie.budgetTracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

	/**
	 * Everything that affects the window, on the date it affects it (BR-4).
	 *
	 * A credit-card purchase is dated by its bill, not by the day it was made,
	 * because that is the day the money is actually owed. Reading it by
	 * `entry_date` would put an August purchase into August while its plan sat
	 * in September, and the variance between them would be nonsense in both
	 * months.
	 */
	@Query("""
			SELECT t FROM TransactionEntity t
			WHERE t.user.id = :userId
			  AND COALESCE(t.plannedExpenseDate, t.entryDate) BETWEEN :from AND :to
			ORDER BY COALESCE(t.plannedExpenseDate, t.entryDate) ASC
			""")
	List<TransactionEntity> findInPeriod(@Param("userId") UUID userId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);
}
