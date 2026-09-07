package ie.budgetTracker.application.plan.dto;

import ie.budgetTracker.domain.plan.DateRange;
import ie.budgetTracker.domain.plan.PeriodKind;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The window a list of figures covers.
 *
 * It travels with the list rather than being assumed by the page, because
 * BR-10 counts occurrences against real dates: "Month" names the window, but
 * only the two dates say where its edges fall, and those edges are the
 * difference between four paydays and five.
 */
public record PeriodWindowResponse(PeriodKind kind, LocalDate from, LocalDate to, String label) {

	/**
	 * Locale-pinned on purpose. Left to the platform default, the same window
	 * would read differently depending on which machine answered the request.
	 */
	private static final DateTimeFormatter DAY_AND_MONTH =
			DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

	private static final DateTimeFormatter MONTH_AND_YEAR =
			DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);

	public static PeriodWindowResponse of(PeriodKind kind, DateRange range) {
		return new PeriodWindowResponse(kind, range.start(), range.end(), labelFor(kind, range));
	}

	/** How the window reads in prose, e.g. "August 2026". */
	private static String labelFor(PeriodKind kind, DateRange range) {
		return switch (kind) {
			case DAY -> range.start().format(DAY_AND_MONTH);
			case WEEK -> "Week of " + range.start().format(DAY_AND_MONTH);
			case MONTH -> range.start().format(MONTH_AND_YEAR);
			case YEAR -> String.valueOf(range.start().getYear());
			// Both ends spelled out: a custom window has no name of its own, and
			// its edges are the only thing that describes it.
			case CUSTOM -> range.start().format(DAY_AND_MONTH) + " to "
					+ range.end().format(DAY_AND_MONTH);
		};
	}
}
