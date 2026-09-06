package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.auth.SessionRepository;
import ie.budgetTracker.domain.identity.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaSessionRepository implements SessionRepository {

	private final SessionJpaRepository sessions;

	JpaSessionRepository(SessionJpaRepository sessions) {
		this.sessions = sessions;
	}

	@Override
	public Session save(Session session) {
		return sessions.save(SessionEntity.from(session)).toDomain();
	}

	@Override
	public Optional<Session> findByTokenHash(String tokenHash) {
		return sessions.findById(tokenHash).map(SessionEntity::toDomain);
	}

	@Override
	public void deleteByTokenHash(String tokenHash) {
		sessions.deleteById(tokenHash);
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		sessions.deleteByUserId(userId);
	}
}
