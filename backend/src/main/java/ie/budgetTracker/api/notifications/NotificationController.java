package ie.budgetTracker.api.notifications;

import ie.budgetTracker.application.notifications.NotificationService;
import ie.budgetTracker.application.notifications.dto.MarkNotificationsReadRequest;
import ie.budgetTracker.application.notifications.dto.NotificationResponse;
import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import ie.budgetTracker.application.notifications.dto.UpdateNotificationSettingsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The derived queue and its settings (BR-12).
 *
 * There is no POST here and no DELETE: a notification is not a thing anybody
 * creates or removes. It exists while the money it warns about is owed, and
 * stops existing when that money moves.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

	private final NotificationService notifications;

	NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	List<NotificationResponse> queue() {
		return notifications.queue();
	}

	@GetMapping("/settings")
	NotificationSettingsResponse settings() {
		return notifications.settings();
	}

	/**
	 * BR-12: one item or the whole queue, through the same write.
	 *
	 * 204: the caller already knows which keys it sent, and the queue it would
	 * be handed back is one it is about to refetch anyway.
	 */
	@PatchMapping("/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@Valid @RequestBody MarkNotificationsReadRequest request) {
		notifications.markRead(request);
	}

	@PatchMapping("/settings")
	NotificationSettingsResponse updateSettings(
			@RequestBody UpdateNotificationSettingsRequest request) {
		return notifications.updateSettings(request);
	}
}
