package ie.budgetTracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An answer already given, kept so a retry can be answered rather than repeated. */
@Entity
@Table(name = "idempotent_request")
public class IdempotentRequestEntity {

	/**
	 * The user is half the key, not a column beside it.
	 *
	 * A key is chosen by the client, so two people can pick the same one, and a
	 * lookup by key alone would hand the second person the first person's
	 * answer (ADR-11).
	 */
	@Embeddable
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		@Column(name = "user_id")
		private UUID userId;

		@Column(name = "idempotency_key")
		private String idempotencyKey;

		protected Key() {
			// JPA.
		}

		Key(UUID userId, String idempotencyKey) {
			this.userId = userId;
			this.idempotencyKey = idempotencyKey;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key
					&& Objects.equals(userId, key.userId)
					&& Objects.equals(idempotencyKey, key.idempotencyKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, idempotencyKey);
		}
	}

	@EmbeddedId
	private Key id;

	private String endpoint;

	@Column(name = "response_body")
	private String responseBody;

	@Column(name = "created_at")
	private Instant createdAt;

	protected IdempotentRequestEntity() {
		// JPA.
	}

	IdempotentRequestEntity(UUID userId, String key, String endpoint, String responseBody,
			Instant createdAt) {
		this.id = new Key(userId, key);
		this.endpoint = endpoint;
		this.responseBody = responseBody;
		this.createdAt = createdAt;
	}

	String endpoint() {
		return endpoint;
	}

	String responseBody() {
		return responseBody;
	}
}
