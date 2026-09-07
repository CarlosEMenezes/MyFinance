package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** No finder here can be called without saying whose categories to read. */
interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

	List<CategoryEntity> findByUserIdOrderByNameAsc(UUID userId);

	Optional<CategoryEntity> findByUserIdAndId(UUID userId, UUID id);
}
