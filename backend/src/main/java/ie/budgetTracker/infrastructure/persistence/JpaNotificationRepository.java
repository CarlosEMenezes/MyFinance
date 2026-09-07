package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.notifications.NotificationRepository;
import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link NotificationRepository}. */
@Repository
class JpaNotificationRepository implements NotificationRepository {

	private final NotificationReadJpaRepository reads;
	private final NotificationSettingsJpaRepository settings;

	JpaNotificationRepository(NotificationReadJpaRepository reads,
			NotificationSettingsJpaRepository settings) {
		this.reads = reads;
		this.settings = settings;
	}

	@Override
	public Map<String, Instant> readAtByKey(UUID userId) {
		return reads.findByIdUserId(userId).stream()
				.collect(Collectors.toMap(read -> read.key().notificationKey(),
						NotificationReadEntity::readAt));
	}

	@Override
	public void markRead(UUID userId, Collection<String> keys, boolean read, Instant at) {
		if (read) {
			reads.saveAll(keys.stream()
					.map(key -> new NotificationReadEntity(userId, key, at))
					.toList());
			return;
		}

		// Unread is the absence of a row, not a row saying false. One state, one
		// representation, and nothing to fall out of step.
		reads.deleteAllById(keys.stream()
				.map(key -> new NotificationReadEntity.Key(userId, key))
				.toList());
	}

	@Override
	public NotificationSettingsResponse settings(UUID userId) {
		return settings.findById(userId)
				.map(NotificationSettingsEntity::toResponse)
				// Somebody who has never opened Settings still gets warned. The
				// default is all three leads, because the useful failure is being
				// told too early rather than not at all.
				.orElseGet(() -> new NotificationSettingsResponse(List.of(10, 5, 2),
						new NotificationSettingsResponse.Channels(true, false, true)));
	}

	@Override
	public NotificationSettingsResponse saveSettings(UUID userId,
			NotificationSettingsResponse update) {

		NotificationSettingsEntity stored = settings.findById(userId).orElse(null);
		if (stored == null) {
			return settings.save(new NotificationSettingsEntity(userId, update)).toResponse();
		}

		stored.apply(update);
		return settings.save(stored).toResponse();
	}
}

interface NotificationReadJpaRepository
		extends JpaRepository<NotificationReadEntity, NotificationReadEntity.Key> {

	List<NotificationReadEntity> findByIdUserId(UUID userId);
}

interface NotificationSettingsJpaRepository
		extends JpaRepository<NotificationSettingsEntity, UUID> {
}
