package ie.budgetTracker.domain.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A due payment with its urgency and read state resolved (BR-12). */
public record QueuedPayment(
		String key,
		String label,
		String detail,
		LocalDate dueDate,
		/** Negative once the payment is overdue. */
		int daysUntilDue,
		BigDecimal amount,
		DueSource source,
		boolean read) {

	public boolean overdue() {
		return daysUntilDue < 0;
	}
}
