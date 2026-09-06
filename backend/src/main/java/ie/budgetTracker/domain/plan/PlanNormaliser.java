package ie.budgetTracker.domain.plan;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * BR-10: what a plan actually costs over a period, counted on real dates.
 *
 * A month holding five paydays plans five. There is no averaging here and
 * there must not be - BR-3's 52/12 is a different figure for a different
 * purpose, lives on {@link Frequency#periodsPerMonth()}, and is named so the
 * two cannot be confused. See CLAUDE.md gotcha 5.
 */
public final class PlanNormaliser {

	private static final int DAYS_PER_WEEK = 7;
	private static final int DAYS_PER_FORTNIGHT = 14;

	private PlanNormaliser() {
	}

	public static int occurrencesIn(Frequency frequency, LocalDate anchor, DateRange range) {
		return frequency == Frequency.MONTHLY
				? countMonthly(anchor, range)
				: countStepped(anchor, range, stepInDays(frequency));
	}

	/** The per-occurrence amount times the number of times it actually lands. */
	public static BigDecimal plannedAmountIn(BigDecimal perOccurrence, Frequency frequency,
			LocalDate anchor, DateRange range) {
		return MoneyCalculator.multiply(perOccurrence, occurrencesIn(frequency, anchor, range));
	}

	private static int stepInDays(Frequency frequency) {
		return frequency == Frequency.WEEKLY ? DAYS_PER_WEEK : DAYS_PER_FORTNIGHT;
	}

	/**
	 * Walks the months the range touches and asks where the anchor lands in each.
	 *
	 * Every occurrence is computed from the *original* anchor, never by stepping
	 * from the previous one. Stepping would lose the day: 31 January plus a month
	 * is 28 February, and plus another month would be 28 March rather than the
	 * 31st the plan is anchored to.
	 */
	private static int countMonthly(LocalDate anchor, DateRange range) {
		int anchorDay = anchor.getDayOfMonth();
		LocalDate cursor = range.start().withDayOfMonth(1);
		int count = 0;

		while (!cursor.isAfter(range.end())) {
			LocalDate occurrence = cursor.withDayOfMonth(
					Math.min(anchorDay, cursor.lengthOfMonth()));

			if (range.contains(occurrence) && !occurrence.isBefore(anchor)) {
				count++;
			}
			cursor = cursor.plusMonths(1);
		}
		return count;
	}

	/**
	 * Steps by a fixed number of days from the anchor.
	 *
	 * Day arithmetic, not month arithmetic, so a daylight-saving change inside
	 * the range cannot add or drop an occurrence - the dates are calendar days
	 * and carry no time of day to shift.
	 */
	private static int countStepped(LocalDate anchor, DateRange range, int stepInDays) {
		long daysUntilStart = ChronoUnit.DAYS.between(anchor, range.start());
		LocalDate occurrence = daysUntilStart > 0
				// Jump straight to the first landing on or after the range start.
				// Ceiling division, spelled out because Math.ceilDiv is Java 18+.
				? anchor.plusDays(((daysUntilStart + stepInDays - 1) / stepInDays) * stepInDays)
				: anchor;
		int count = 0;

		while (!occurrence.isAfter(range.end())) {
			count++;
			occurrence = occurrence.plusDays(stepInDays);
		}
		return count;
	}
}
