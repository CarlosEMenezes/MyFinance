package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.auth.CredentialsRepository;
import ie.budgetTracker.domain.identity.Credentials;
import ie.budgetTracker.domain.identity.User;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaCredentialsRepository implements CredentialsRepository {

	private final UserJpaRepository users;
	private final Clock clock;

	JpaCredentialsRepository(UserJpaRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	@Override
	public boolean emailIsTaken(String normalisedEmail) {
		return users.existsByEmail(normalisedEmail);
	}

	@Override
	public User register(String normalisedEmail, String passwordHash, User profile) {
		return users.save(UserEntity.registering(normalisedEmail, passwordHash, profile,
				clock.instant())).toDomain();
	}

	@Override
	public Optional<StoredCredentials> findByEmail(String normalisedEmail) {
		return users.findByEmail(normalisedEmail)
				.map(entity -> new StoredCredentials(entity.getId(),
						new Credentials(entity.getEmail(), entity.getPasswordHash())));
	}

	@Override
	public Optional<User> findProfile(UUID userId) {
		return users.findById(userId).map(UserEntity::toDomain);
	}
}
