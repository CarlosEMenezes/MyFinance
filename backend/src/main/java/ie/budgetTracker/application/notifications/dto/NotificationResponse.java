package ie.budgetTracker.application.notifications.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.notifications.DueSource;
import ie.budgetTracker.domain.notifications.QueuedPayment;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One item in the derived queue (BR-12).
 *
 * `daysUntilDue` is negative once a payment is overdue, and is sent rather
 * than left to be worked out from the date: a screen computing it from its own
 * clock would disagree with the server whenever the two were in different time
 * zones, and "due today" would become "due yesterday" for somebody travelling.
 *
 * `readAt` rather than a boolean, because it is the only thing about a
 * notification that is stored, and when it was seen is worth keeping.
 */
public record NotificationResponse(
		String key,
		String label,
		String detail,
		LocalDate dueDate,
		int daysUntilDue,
		long amount,
		DueSource sourceType,
		Instant readAt) {

	public static NotificationResponse from(QueuedPayment payment, Instant readAt) {
		return new NotificationResponse(
				payment.key(),
				payment.label(),
				payment.detail(),
				payment.dueDate(),
				payment.daysUntilDue(),
				Money.toMinorUnits(payment.amount()),
				payment.source(),
				readAt);
	}
}
