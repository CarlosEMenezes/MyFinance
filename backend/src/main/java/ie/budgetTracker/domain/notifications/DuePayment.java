package ie.budgetTracker.domain.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One thing falling due, before the queue works out how urgent it is.
 *
 * The key is stable across recomputations - it is what read state is stored
 * against. If a key changed between builds, an item marked read would come
 * back unread.
 */
public record DuePayment(
		String key,
		String label,
		String detail,
		LocalDate dueDate,
		BigDecimal amount,
		DueSource source) {
}
