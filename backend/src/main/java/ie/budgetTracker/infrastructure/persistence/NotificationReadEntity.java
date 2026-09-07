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

/**
 * That somebody has seen a notification (BR-12).
 *
 * The only part of a notification that is stored. Everything else is derived
 * on every read, so a paid card drops out of the queue without anything having
 * to delete a row.
 */
@Entity
@Table(name = "notification_read")
public class NotificationReadEntity {

	/** The user is half the key: a queue key is only unique within one person. */
	@Embeddable
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		@Column(name = "user_id")
		private UUID userId;

		@Column(name = "notification_key")
		private String notificationKey;

		protected Key() {
			// JPA.
		}

		Key(UUID userId, String notificationKey) {
			this.userId = userId;
			this.notificationKey = notificationKey;
		}

		String notificationKey() {
			return notificationKey;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key
					&& Objects.equals(userId, key.userId)
					&& Objects.equals(notificationKey, key.notificationKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, notificationKey);
		}
	}

	@EmbeddedId
	private Key id;

	@Column(name = "read_at")
	private Instant readAt;

	protected NotificationReadEntity() {
		// JPA.
	}

	NotificationReadEntity(UUID userId, String notificationKey, Instant readAt) {
		this.id = new Key(userId, notificationKey);
		this.readAt = readAt;
	}

	Key key() {
		return id;
	}

	Instant readAt() {
		return readAt;
	}
}
