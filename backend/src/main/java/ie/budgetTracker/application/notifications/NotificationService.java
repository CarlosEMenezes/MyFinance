package ie.budgetTracker.application.notifications;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.notifications.dto.MarkNotificationsReadRequest;
import ie.budgetTracker.application.notifications.dto.NotificationResponse;
import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import ie.budgetTracker.application.notifications.dto.UpdateNotificationSettingsRequest;
import ie.budgetTracker.domain.notifications.DuePayment;
import ie.budgetTracker.domain.notifications.DuePaymentQueue;
import ie.budgetTracker.domain.notifications.QueuedPayment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The notification queue, derived every time (BR-12).
 *
 * Nothing about a notification is stored except whether it has been read. A
 * paid card therefore drops out of the queue on its own: there is no row for
 * anything to forget to delete, and no way for a warning to outlive the money
 * it was warning about.
 *
 * The lead times filter what is shown, but never hide what has already
 * happened: with every warning turned off the widest lead is zero, which still
 * admits anything due today or overdue.
 */
@Service
public class NotificationService {

	private final DuePayments duePayments;
	private final NotificationRepository notifications;
	private final CurrentUser currentUser;
	private final Clock clock;

	public NotificationService(DuePayments duePayments, NotificationRepository notifications,
			CurrentUser currentUser, Clock clock) {
		this.duePayments = duePayments;
		this.notifications = notifications;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> queue() {
		UUID user = currentUser.id();
		LocalDate today = LocalDate.now(clock);

		Map<String, Instant> readAt = notifications.readAtByKey(user);
		List<DuePayment> due = duePayments.forUser(user, today);
		Set<Integer> leads = new HashSet<>(notifications.settings(user).leadDays());

		return DuePaymentQueue.build(due, today, leads, readAt.keySet()).stream()
				.map(payment -> NotificationResponse.from(payment, readAt.get(payment.key())))
				.toList();
	}

	/** BR-12: the count that drives the nav badge. */
	@Transactional(readOnly = true)
	public int unreadCount() {
		UUID user = currentUser.id();
		LocalDate today = LocalDate.now(clock);

		List<QueuedPayment> queue = DuePaymentQueue.build(duePayments.forUser(user, today), today,
				new HashSet<>(notifications.settings(user).leadDays()),
				notifications.readAtByKey(user).keySet());

		return DuePaymentQueue.unreadCount(queue);
	}

	@Transactional
	public void markRead(MarkNotificationsReadRequest request) {
		notifications.markRead(currentUser.id(), request.keys(), request.read(),
				Instant.now(clock));
	}

	@Transactional(readOnly = true)
	public NotificationSettingsResponse settings() {
		return notifications.settings(currentUser.id());
	}

	/**
	 * BR-12: leads are any subset of {10, 5, 2}.
	 *
	 * A value outside that set is refused rather than ignored: silently dropping
	 * it would leave the sender believing in a warning that will never arrive.
	 */
	@Transactional
	public NotificationSettingsResponse updateSettings(UpdateNotificationSettingsRequest request) {
		NotificationSettingsResponse current = settings();

		List<Integer> leadDays = request.leadDays() == null
				? current.leadDays()
				: validated(request.leadDays());

		return notifications.saveSettings(currentUser.id(), new NotificationSettingsResponse(
				leadDays,
				request.channels() == null ? current.channels() : request.channels()));
	}

	private static List<Integer> validated(List<Integer> leadDays) {
		for (Integer lead : leadDays) {
			if (lead == null || !DuePaymentQueue.ALLOWED_LEAD_DAYS.contains(lead)) {
				throw AppException.invalid("leadDays",
						"Lead times are any of 10, 5 and 2 days, and nothing else");
			}
		}
		return leadDays.stream().distinct().sorted().toList();
	}
}
