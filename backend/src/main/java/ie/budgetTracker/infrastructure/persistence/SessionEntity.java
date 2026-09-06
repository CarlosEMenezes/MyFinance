package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.identity.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A session row (ADR-11).
 *
 * The primary key is the token's SHA-256, not the token: there is no column
 * here that could be replayed if the table leaked.
 */
@Entity
@Table(name = "user_session")
public class SessionEntity {

	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	protected SessionEntity() {
		// JPA.
	}

	static SessionEntity from(Session session) {
		SessionEntity entity = new SessionEntity();
		entity.tokenHash = session.tokenHash();
		entity.userId = session.userId();
		entity.createdAt = session.createdAt();
		entity.expiresAt = session.expiresAt();
		entity.lastSeenAt = session.lastSeenAt();
		return entity;
	}

	Session toDomain() {
		return new Session(tokenHash, userId, createdAt, expiresAt, lastSeenAt);
	}
}
