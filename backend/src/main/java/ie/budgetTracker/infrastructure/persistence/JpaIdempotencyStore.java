package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.support.IdempotencyStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link IdempotencyStore}. */
@Repository
class JpaIdempotencyStore implements IdempotencyStore {

	private final IdempotentRequestJpaRepository requests;
	private final Clock clock;

	JpaIdempotencyStore(IdempotentRequestJpaRepository requests, Clock clock) {
		this.requests = requests;
		this.clock = clock;
	}

	@Override
	public Optional<String> replay(UUID userId, String key, String endpoint) {
		return requests.findById(new IdempotentRequestEntity.Key(userId, key))
				.map(seen -> {
					if (!seen.endpoint().equals(endpoint)) {
						// Replaying a loan as a transaction would be worse than
						// creating a second one, so this is reported rather than
						// treated as a match.
						throw AppException.conflict(
								"That idempotency key was already used for a different request");
					}
					return seen.responseBody();
				});
	}

	@Override
	public void remember(UUID userId, String key, String endpoint, String responseBody) {
		requests.save(new IdempotentRequestEntity(userId, key, endpoint, responseBody,
				Instant.now(clock)));
	}
}

/** Lookup is by the composite key, so a key alone can never reach a row. */
interface IdempotentRequestJpaRepository
		extends JpaRepository<IdempotentRequestEntity, IdempotentRequestEntity.Key> {
}
