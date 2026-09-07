package ie.budgetTracker.domain.plan;

import ie.budgetTracker.domain.identity.WeekStart;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Which dates a named period actually covers (BR-10).
 *
 * This is not presentation. BR-10 counts occurrences against real dates, so
 * where the window begins and ends is what decides whether a weekly plan lands
 * four times or five - and that difference is a month of rent. The window is
 * therefore the server's answer and travels with every list it applies to.
 *
 * A week begins where the user says it begins. The same Monday is in one
 * window for somebody whose week starts on Monday and in another for somebody
 * whose week starts on Sunday, and both are right.
 */
public final class PeriodResolver {

	private static final int DAYS_IN_A_WEEK = 7;

	private PeriodResolver() {
	}

	public static DateRange resolve(PeriodKind kind, LocalDate today, WeekStart weekStart) {
		return switch (kind) {
			case DAY -> new DateRange(today, today);
			case WEEK -> week(today, weekStart);
			case MONTH -> new DateRange(today.withDayOfMonth(1),
					today.withDayOfMonth(today.lengthOfMonth()));
			case YEAR -> new DateRange(today.withDayOfYear(1),
					today.withDayOfYear(today.lengthOfYear()));
			// Deliberately not resolvable. Choosing dates here would be the code
			// picking the range the user asked to pick themselves.
			case CUSTOM -> throw new IllegalArgumentException(
					"a CUSTOM period is the range it was given, and cannot be derived from today");
		};
	}

	/**
	 * The week containing today, looking backwards to its first day.
	 *
	 * Backwards rather than forwards: on a Sunday, somebody whose week begins on
	 * Monday is in the week that is ending, not the one about to begin.
	 */
	private static DateRange week(LocalDate today, WeekStart weekStart) {
		DayOfWeek firstDay = weekStart == WeekStart.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;

		long daysSinceStart = Math.floorMod(
				today.getDayOfWeek().getValue() - firstDay.getValue(), DAYS_IN_A_WEEK);
		LocalDate start = today.minus(daysSinceStart, ChronoUnit.DAYS);

		return new DateRange(start, start.plusDays(DAYS_IN_A_WEEK - 1L));
	}
}
