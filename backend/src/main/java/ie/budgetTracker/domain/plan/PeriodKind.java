package ie.budgetTracker.domain.plan;

/**
 * The window a figure covers.
 *
 * Not a duration: BR-10 counts landings on real dates, so "a month" means the
 * calendar month with its own number of days, not thirty of them. CUSTOM is
 * the one member that cannot be worked out from today - it is whatever range
 * the user asked for.
 */
public enum PeriodKind {
	DAY,
	WEEK,
	MONTH,
	YEAR,
	CUSTOM
}
