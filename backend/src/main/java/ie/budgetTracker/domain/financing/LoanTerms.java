package ie.budgetTracker.domain.financing;

import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.plan.Frequency;
import java.math.BigDecimal;

/**
 * Money received as a principal and repaid as n instalments of A (BR-7).
 *
 * BR-2: a loan is not income. It raises what is available by the principal and
 * what is owed by the whole repayment, and the difference between those is the
 * interest - which is the only lasting effect on the position.
 */
public record LoanTerms(
		BigDecimal principal,
		int instalmentCount,
		BigDecimal instalmentAmount,
		Frequency frequency,
		int instalmentsPaid) {

	public LoanTerms {
		if (instalmentCount <= 0) {
			throw new IllegalArgumentException(
					"instalmentCount must be a positive whole number, but was " + instalmentCount);
		}
		if (!MoneyCalculator.isPositive(principal)) {
			throw new IllegalArgumentException("principal must be positive, but was " + principal);
		}
		if (!MoneyCalculator.isPositive(instalmentAmount)) {
			throw new IllegalArgumentException(
					"instalmentAmount must be positive, but was " + instalmentAmount);
		}
		if (instalmentsPaid < 0 || instalmentsPaid > instalmentCount) {
			throw new IllegalArgumentException("instalmentsPaid must be between 0 and "
					+ instalmentCount + ", but was " + instalmentsPaid);
		}
	}

	public int instalmentsRemaining() {
		return instalmentCount - instalmentsPaid;
	}
}
