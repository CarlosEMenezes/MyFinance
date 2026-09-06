package ie.budgetTracker.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {

	void deleteByUserId(java.util.UUID userId);
}
