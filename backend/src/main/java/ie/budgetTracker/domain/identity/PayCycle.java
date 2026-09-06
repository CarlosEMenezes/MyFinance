package ie.budgetTracker.domain.identity;

/**
 * How often someone is paid.
 *
 * IRREGULAR is the point of the app: it is the default, not the exception.
 */
public enum PayCycle {
	WEEKLY,
	FORTNIGHTLY,
	MONTHLY,
	IRREGULAR
}
