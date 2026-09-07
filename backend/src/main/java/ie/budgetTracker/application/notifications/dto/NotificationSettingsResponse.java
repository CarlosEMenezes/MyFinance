package ie.budgetTracker.application.notifications.dto;

import java.util.List;

/**
 * Which lead times and channels are on (BR-12).
 *
 * `leadDays` is any subset of {10, 5, 2} and nothing else. Stored and sent as
 * a set rather than three flags, because the rule is about a set: three
 * booleans would let a fourth lead time be added without anybody noticing the
 * rule said otherwise.
 */
public record NotificationSettingsResponse(List<Integer> leadDays, Channels channels) {

	public record Channels(boolean push, boolean email, boolean weeklySummary) {
	}
}
