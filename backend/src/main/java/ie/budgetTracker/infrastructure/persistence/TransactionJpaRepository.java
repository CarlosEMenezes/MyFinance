package ie.budgetTracker.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Writes only, for now.
 *
 * Reading transactions is what the dashboard does, and the dashboard is spec
 * §6 step 8. A finder added before anything asks a question of it would be
 * scaffolding (spec §0.4).
 */
interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {
}
