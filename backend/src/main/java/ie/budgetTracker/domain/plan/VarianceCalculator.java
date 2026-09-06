package ie.budgetTracker.domain.plan;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;

/**
 * BR-9: how reality differs from the plan.
 *
 * The figure is {@code real - planned} for BOTH kinds of category. Only the
 * tone differs: over plan is welcome on an earning and unwelcome on an expense.
 *
 * The prototype flips the sign for expenses so that underspending shows
 * positive. The spec does not, and the spec wins. A single sign convention is
 * also what lets a column of variances be summed and sorted without first
 * asking what kind of row each one is - which is a property worth more than
 * the small readability the flip buys. See CLAUDE.md gotcha 18.
 */
public final class VarianceCalculator {

	private VarianceCalculator() {
	}

	public static Variance varianceOf(CategoryType type, BigDecimal planned, BigDecimal real) {
		BigDecimal amount = MoneyCalculator.subtract(real, planned);
		return new Variance(amount, toneOf(type, amount));
	}

	private static VarianceTone toneOf(CategoryType type, BigDecimal amount) {
		if (MoneyCalculator.isZero(amount)) {
			return VarianceTone.NEUTRAL;
		}
		boolean overPlan = MoneyCalculator.isPositive(amount);
		return switch (type) {
			case EARNING -> overPlan ? VarianceTone.GOOD : VarianceTone.BAD;
			case EXPENSE -> overPlan ? VarianceTone.BAD : VarianceTone.GOOD;
		};
	}
}
