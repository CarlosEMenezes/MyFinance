package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.domain.identity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link UserRepository}. */
@Repository
class JpaUserRepository implements UserRepository {

	private final UserJpaRepository users;

	JpaUserRepository(UserJpaRepository users) {
		this.users = users;
	}

	@Override
	public Optional<User> findById(UUID id) {
		return users.findById(id).map(UserEntity::toDomain);
	}

	@Override
	public User save(User user) {
		// Updates the managed row in place rather than replacing it, so a partial
		// edit cannot blank a column the request never mentioned.
		UserEntity entity = users.findById(user.id()).orElseGet(() -> UserEntity.from(user));
		entity.apply(user);
		return users.save(entity).toDomain();
	}
}
