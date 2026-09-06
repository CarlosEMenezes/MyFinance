package ie.budgetTracker.domain.notifications;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * BR-12: what is coming due, in the order it is coming.
 *
 * Derived, never stored. The queue is assembled from card bills, loans,
 * instalments, direct debits and subscriptions each time it is asked for, and
 * only read state is persisted — so a paid card or a settled loan drops out on
 * its own rather than leaving a notification behind to be tidied up.
 */
public final class DuePaymentQueue {

	/** BR-12: the lead times a person may choose between. */
	public static final Set<Integer> ALLOWED_LEAD_DAYS = Set.of(10, 5, 2);

	private DuePaymentQueue() {
	}

	/**
	 * The payments worth showing, soonest first.
	 *
	 * Shown when {@code daysUntilDue <= max(enabled lead days)}. With no lead
	 * time enabled the widest is zero, which still admits anything due today or
	 * already overdue: turning every warning off must not hide money that has
	 * already left.
	 */
	public static List<QueuedPayment> build(List<DuePayment> payments, LocalDate today,
			Set<Integer> enabledLeadDays, Set<String> readKeys) {

		int widest = widestLead(enabledLeadDays);

		return payments.stream()
				.map(payment -> toQueued(payment, today, readKeys))
				.filter(payment -> payment.daysUntilDue() <= widest)
				// Stable sort, so two payments falling on the same day keep the order
				// they were assembled in rather than swapping between requests.
				.sorted(Comparator.comparingInt(QueuedPayment::daysUntilDue))
				.toList();
	}

	/**
	 * How many payments a lead time catches, counted over the WHOLE queue.
	 *
	 * The number exists to say what turning this option on would add, so
	 * counting only what is already shown would make every enabled option report
	 * itself. Overdue items are excluded: they are past every lead time, and
	 * attributing them to one would overstate it.
	 */
	public static int countCaughtBy(List<DuePayment> payments, LocalDate today, int leadDays) {
		requireAllowedLead(leadDays);

		return (int) payments.stream()
				.mapToLong(payment -> daysUntil(payment, today))
				.filter(days -> days >= 0 && days <= leadDays)
				.count();
	}

	/** BR-12: this is the count that drives the nav badge. */
	public static int unreadCount(List<QueuedPayment> queue) {
		return (int) queue.stream().filter(payment -> !payment.read()).count();
	}

	private static int widestLead(Set<Integer> enabledLeadDays) {
		enabledLeadDays.forEach(DuePaymentQueue::requireAllowedLead);
		return enabledLeadDays.stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	private static void requireAllowedLead(int leadDays) {
		if (!ALLOWED_LEAD_DAYS.contains(leadDays)) {
			throw new IllegalArgumentException(
					"lead days must be one of " + ALLOWED_LEAD_DAYS + ", but was " + leadDays);
		}
	}

	private static QueuedPayment toQueued(DuePayment payment, LocalDate today,
			Set<String> readKeys) {
		return new QueuedPayment(
				payment.key(),
				payment.label(),
				payment.detail(),
				payment.dueDate(),
				(int) daysUntil(payment, today),
				payment.amount(),
				payment.source(),
				readKeys.contains(payment.key()));
	}

	private static long daysUntil(DuePayment payment, LocalDate today) {
		return ChronoUnit.DAYS.between(today, payment.dueDate());
	}
}
