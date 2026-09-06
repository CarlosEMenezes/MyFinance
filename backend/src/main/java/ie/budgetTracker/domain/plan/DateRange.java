package ie.budgetTracker.domain.plan;

import java.time.LocalDate;

/**
 * A window of whole days, both ends included.
 *
 * Inclusive at both ends because BR-10 counts landings: a plan falling on the
 * last day of the period is a cost in that period. A half-open range would
 * silently drop it.
 */
public record DateRange(LocalDate start, LocalDate end) {

	public DateRange {
		if (end.isBefore(start)) {
			throw new IllegalArgumentException(
					"end must not be before start, but was " + end + " against " + start);
		}
	}

	boolean contains(LocalDate date) {
		return !date.isBefore(start) && !date.isAfter(end);
	}
}
