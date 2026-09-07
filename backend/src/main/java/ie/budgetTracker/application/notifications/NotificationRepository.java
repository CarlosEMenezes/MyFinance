package ie.budgetTracker.application.notifications;

import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The only two things about notifications that are persisted (BR-12).
 *
 * The queue itself is derived on every read, so there is nothing here that
 * stores a notification. What cannot be derived is whether somebody has seen
 * one, and what they asked to be warned about.
 */
public interface NotificationRepository {

	/** When each item was read, by key. Absent means unread. */
	Map<String, Instant> readAtByKey(UUID userId);

	/**
	 * Marks items read or unread.
	 *
	 * Marking one and marking the whole queue are the same write, because two
	 * endpoints would need two optimistic updates that had to agree.
	 */
	void markRead(UUID userId, Collection<String> keys, boolean read, Instant at);

	NotificationSettingsResponse settings(UUID userId);

	NotificationSettingsResponse saveSettings(UUID userId, NotificationSettingsResponse settings);
}
