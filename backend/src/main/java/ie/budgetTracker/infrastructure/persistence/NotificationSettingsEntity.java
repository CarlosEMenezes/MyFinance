package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Which lead times and channels somebody asked for (BR-12). */
@Entity
@Table(name = "notification_settings")
public class NotificationSettingsEntity {

	/** All three, which is the default a new account starts with. */
	static final String DEFAULT_LEAD_DAYS = "10,5,2";

	@Id
	@Column(name = "user_id")
	private UUID userId;

	/**
	 * A short list rather than three flags.
	 *
	 * BR-12 is a rule about a *set* of lead times; three booleans would let a
	 * fourth be added without anybody noticing the rule said otherwise.
	 */
	@Column(name = "lead_days")
	private String leadDays;

	private boolean push;

	private boolean email;

	@Column(name = "weekly_summary")
	private boolean weeklySummary;

	protected NotificationSettingsEntity() {
		// JPA.
	}

	NotificationSettingsEntity(UUID userId, NotificationSettingsResponse settings) {
		this.userId = userId;
		apply(settings);
	}

	final void apply(NotificationSettingsResponse settings) {
		this.leadDays = settings.leadDays().stream()
				.map(String::valueOf)
				.collect(Collectors.joining(","));
		this.push = settings.channels().push();
		this.email = settings.channels().email();
		this.weeklySummary = settings.channels().weeklySummary();
	}

	NotificationSettingsResponse toResponse() {
		return new NotificationSettingsResponse(parse(leadDays),
				new NotificationSettingsResponse.Channels(push, email, weeklySummary));
	}

	/** An empty string is "warn me about nothing", which is a real choice. */
	private static List<Integer> parse(String stored) {
		if (stored == null || stored.isBlank()) {
			return List.of();
		}
		return Arrays.stream(stored.split(","))
				.map(String::trim)
				.filter(day -> !day.isEmpty())
				.map(Integer::valueOf)
				.toList();
	}
}
