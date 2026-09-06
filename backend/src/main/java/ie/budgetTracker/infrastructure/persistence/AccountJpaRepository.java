package ie.budgetTracker.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

	List<AccountEntity> findByUserIdOrderByNameAsc(UUID userId);

	Optional<AccountEntity> findByUserIdAndId(UUID userId, UUID id);
}
