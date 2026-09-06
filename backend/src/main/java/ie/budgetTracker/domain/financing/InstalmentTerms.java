package ie.budgetTracker.domain.financing;

import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.plan.Frequency;
import java.math.BigDecimal;

/**
 * A purchase spread over n instalments of A against a cash price P (BR-6).
 *
 * Validated at construction, so no analysis has to defend against terms that
 * cannot describe a real plan.
 */
public record InstalmentTerms(
		BigDecimal cashPrice,
		int instalmentCount,
		BigDecimal instalmentAmount,
		Frequency frequency) {

	public InstalmentTerms {
		if (instalmentCount <= 0) {
			throw new IllegalArgumentException(
					"instalmentCount must be a positive whole number, but was " + instalmentCount);
		}
		if (!MoneyCalculator.isPositive(cashPrice)) {
			throw new IllegalArgumentException("cashPrice must be positive, but was " + cashPrice);
		}
		if (!MoneyCalculator.isPositive(instalmentAmount)) {
			throw new IllegalArgumentException(
					"instalmentAmount must be positive, but was " + instalmentAmount);
		}
	}
}
