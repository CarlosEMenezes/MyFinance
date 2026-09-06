package ie.budgetTracker.domain.cards;

/**
 * A credit card's billing cycle (BR-4).
 *
 * Both days are validated to 1-28 at construction, so no code downstream has to
 * ask what happens on the 31st of February. That is the whole reason for the
 * range: every day in it exists in every month, which means BR-4 needs no
 * clamping rule anywhere.
 *
 * BR-5: a debit card has no cycle at all, so it never holds one of these.
 */
public record StatementCycle(int closingDay, int dueDay) {

	/** The last day that exists in every month, February included. */
	public static final int LAST_CYCLE_DAY = 28;

	private static final int FIRST_CYCLE_DAY = 1;

	public StatementCycle {
		requireCycleDay(closingDay, "closingDay");
		requireCycleDay(dueDay, "dueDay");
	}

	private static void requireCycleDay(int day, String name) {
		if (day < FIRST_CYCLE_DAY || day > LAST_CYCLE_DAY) {
			throw new IllegalArgumentException(
					name + " must be between " + FIRST_CYCLE_DAY + " and " + LAST_CYCLE_DAY
							+ ", so that it exists in every month, but was " + day);
		}
	}

	/**
	 * BR-4's second month roll. When the bill falls due on or before the day the
	 * statement closes, that due date must belong to the following month -
	 * otherwise the bill would be due before the statement it settles existed.
	 */
	boolean billFallsInTheMonthAfterClosing() {
		return dueDay <= closingDay;
	}
}
