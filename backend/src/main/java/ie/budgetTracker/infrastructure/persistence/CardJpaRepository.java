package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder reaches the owner through the account, because that is where a
 * card's owner actually is. There is no method here that can be called without
 * a user id (ADR-11).
 */
interface CardJpaRepository extends JpaRepository<CardEntity, UUID> {

	List<CardEntity> findByAccountUserIdOrderByNameAsc(UUID userId);

	Optional<CardEntity> findByAccountUserIdAndId(UUID userId, UUID id);
}
