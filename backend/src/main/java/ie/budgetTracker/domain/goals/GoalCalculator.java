package ie.budgetTracker.domain.goals;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BR-11: what reaching a savings target actually takes.
 *
 * The horizon is months multiplied out — daily 30.4, weekly 4.33 — and not
 * BR-10's real-date counting. That is not an inconsistency: a category plan
 * lands on actual dates and a month holding five paydays really does cost
 * five, whereas a goal has no anchor date at all. Counting calendar
 * contributions would answer a question nobody asked.
 */
public final class GoalCalculator {

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

	private GoalCalculator() {
	}

	public static BigDecimal periodsUntilTarget(ContributionFrequency frequency, int months) {
		requireFutureTarget(months);
		return frequency.periodsIn(months);
	}

	public static GoalPlan plan(BigDecimal targetAmount, BigDecimal savedAmount,
			ContributionFrequency frequency, int months) {

		requireFutureTarget(months);
		if (!MoneyCalculator.isPositive(targetAmount)) {
			throw new IllegalArgumentException(
					"targetAmount must be positive, but was " + targetAmount);
		}

		// Floored at zero: a reached goal needs nothing, and a negative gap would
		// turn into a negative contribution, which is not advice.
		BigDecimal gap = MoneyCalculator.atLeastZero(
				MoneyCalculator.subtract(targetAmount, savedAmount));

		return new GoalPlan(
				gap,
				MoneyCalculator.divide(gap, frequency.periodsIn(months)),
				MoneyCalculator.divide(gap, BigDecimal.valueOf(months)));
	}

	/**
	 * How much of the target is saved, as a whole percentage.
	 *
	 * Clamped to 0-100. Over-saving reads 100% rather than 137%, because the bar
	 * it drives cannot show more than full and a figure that disagrees with the
	 * bar beside it is worse than a rounded one. A zero target reads 0% rather
	 * than dividing by it.
	 */
	public static int progressPercent(BigDecimal targetAmount, BigDecimal savedAmount) {
		if (!MoneyCalculator.isPositive(targetAmount) || !MoneyCalculator.isPositive(savedAmount)) {
			return 0;
		}
		int percent = savedAmount.multiply(ONE_HUNDRED)
				.divide(targetAmount, 0, RoundingMode.HALF_UP)
				.intValue();

		return Math.min(percent, 100);
	}

	/**
	 * Whether the monthly requirement fits in what the plan leaves spare.
	 *
	 * `monthlySpare` is planned in minus planned out — a property of the whole
	 * plan, not of this goal. That is the point of the rule: a goal is not
	 * feasible on its own terms, only against everything else being paid for.
	 */
	public static Feasibility assess(BigDecimal monthlyRequirement, BigDecimal monthlySpare) {
		BigDecimal surplus = MoneyCalculator.subtract(monthlySpare, monthlyRequirement);
		return new Feasibility(!MoneyCalculator.isNegative(surplus), surplus);
	}

	private static void requireFutureTarget(int months) {
		if (months <= 0) {
			throw new IllegalArgumentException(
					"months until the target must be positive, but was " + months);
		}
	}
}
