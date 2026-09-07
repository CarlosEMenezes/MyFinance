package ie.budgetTracker.application.notifications.dto;

import java.util.List;

/**
 * `PATCH /notifications/settings` — which lead times and channels are on.
 *
 * Both optional, because the Settings page saves one control at a time. A null
 * means "not mentioned"; an empty `leadDays` list means "warn me about
 * nothing", which is a real choice and not the same thing.
 */
public record UpdateNotificationSettingsRequest(
		List<Integer> leadDays,
		NotificationSettingsResponse.Channels channels) {
}
