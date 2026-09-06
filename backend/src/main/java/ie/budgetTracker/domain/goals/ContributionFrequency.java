package ie.budgetTracker.domain.goals;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;

/**
 * How often someone puts money toward a goal (BR-11).
 *
 * A separate vocabulary from BR-6's Frequency, and deliberately so. This one
 * carries DAILY, which has no meaningful periodsPerYear for an APR conversion;
 * BR-6's carries FORTNIGHTLY, which nobody saves on. Sharing one enum would
 * mean one of them accepting a member it cannot answer for.
 *
 * The factors are months multiplied out, not calendar dates counted. A goal is
 * a smooth target, not a schedule: there is no anchor date it lands on, so
 * BR-10's real-date counting would be answering a question nobody asked.
 */
public enum ContributionFrequency {

	DAILY("30.4"),
	WEEKLY("4.33"),
	MONTHLY("1");

	private final String periodsPerMonth;

	ContributionFrequency(String periodsPerMonth) {
		this.periodsPerMonth = periodsPerMonth;
	}

	public BigDecimal periodsPerMonth() {
		return new BigDecimal(periodsPerMonth);
	}

	/** How many contributions fit in the months remaining. */
	BigDecimal periodsIn(int months) {
		return periodsPerMonth().multiply(BigDecimal.valueOf(months),
				MoneyCalculator.RATE_CONTEXT);
	}
}
