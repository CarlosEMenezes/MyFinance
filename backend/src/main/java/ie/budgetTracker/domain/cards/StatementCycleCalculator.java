package ie.budgetTracker.domain.cards;

import java.time.LocalDate;

/**
 * BR-4: when spending on a credit card is actually billed.
 *
 * Two month rolls, and they are independent. Conflating them is the mistake
 * this class exists to prevent:
 *
 *   1. A purchase after the closing day joins the *next* statement.
 *   2. A due day on or before the closing day falls the month *after* the
 *      statement closes.
 *
 * Both can apply to the same purchase, and then the bill is two months out.
 * The planned-expense date is this computed date, never the purchase date.
 */
public final class StatementCycleCalculator {

	private StatementCycleCalculator() {
	}

	public static LocalDate billDateFor(LocalDate purchaseDate, StatementCycle cycle) {
		// Roll one: which statement the purchase lands on.
		LocalDate closingMonth = purchaseDate.getDayOfMonth() <= cycle.closingDay()
				? purchaseDate
				: purchaseDate.plusMonths(1);

		// Roll two: when that statement's bill falls due.
		LocalDate dueMonth = cycle.billFallsInTheMonthAfterClosing()
				? closingMonth.plusMonths(1)
				: closingMonth;

		// No clamping: the day is 1-28, so it exists in whatever month we landed in.
		return dueMonth.withDayOfMonth(cycle.dueDay());
	}

	/**
	 * The next time a bill on this due day actually falls, counting today.
	 *
	 * Used for the notification queue (BR-12) and for a card's "next bill" line,
	 * where the question is not where a purchase lands but when the next payment
	 * is owed.
	 */
	public static LocalDate nextDueDateOnOrAfter(LocalDate from, int dueDay) {
		// Validated by the same rule, so a caller cannot slip a 31st past here by
		// skipping the cycle type.
		new StatementCycle(StatementCycle.LAST_CYCLE_DAY, dueDay);

		LocalDate thisMonth = from.withDayOfMonth(dueDay);
		return thisMonth.isBefore(from) ? thisMonth.plusMonths(1) : thisMonth;
	}

	/**
	 * The three dates a card screen shows, all from one cycle (BR-4).
	 *
	 * Computed here rather than on the screen: the cycle is persisted, and
	 * ADR-7 puts every persisted figure on this side. The frontend renders
	 * these; it does not derive them.
	 *
	 * "The day after closing" is taken as a real date rather than as
	 * {@code closingDay + 1}, so a card closing on the 28th of February steps to
	 * the 1st of March and joins March's statement instead of asking what the
	 * 29th means.
	 */
	public static CardCycleDates cycleDatesFor(LocalDate today, StatementCycle cycle) {
		LocalDate closingDayThisMonth = today.withDayOfMonth(cycle.closingDay());

		return new CardCycleDates(
				nextDueDateOnOrAfter(today, cycle.dueDay()),
				billDateFor(closingDayThisMonth, cycle),
				billDateFor(closingDayThisMonth.plusDays(1), cycle));
	}
}
