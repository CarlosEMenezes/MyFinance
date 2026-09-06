package ie.budgetTracker.domain.plan;

import java.math.BigDecimal;

/**
 * How often a planned amount, an instalment or a loan repayment recurs.
 *
 * BR-17 keeps this vocabulary separate from recurrence rules on purpose.
 * {@code periodsPerYear} is defined for 52, 26 and 12 only, and BR-6's APR
 * conversion compounds by it; a DAILY or YEARLY member would make that
 * conversion meaningless. When recurrence arrives in Phase 2 it gets its own
 * enum, and the type system keeps the two apart.
 */
public enum Frequency {

	WEEKLY(52),
	FORTNIGHTLY(26),
	MONTHLY(12);

	private final int periodsPerYear;

	Frequency(int periodsPerYear) {
		this.periodsPerYear = periodsPerYear;
	}

	/** BR-6: how many times a periodic rate compounds into an APR. */
	public int periodsPerYear() {
		return periodsPerYear;
	}

	/**
	 * BR-3's averaged figure, for the derived read-only rows only.
	 *
	 * This is NOT how a category plan is counted. BR-10 counts real dates, so a
	 * month holding five paydays plans five; this returns 52/12 = 4.333... and is
	 * the right answer only for a commitment average. See PlanNormaliser.
	 */
	public BigDecimal periodsPerMonth() {
		return BigDecimal.valueOf(periodsPerYear).divide(BigDecimal.valueOf(12),
				ie.budgetTracker.domain.money.MoneyCalculator.RATE_CONTEXT);
	}
}
