package ie.budgetTracker.domain.financing;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;

/**
 * BR-7: what a loan costs, and what settling it today would save.
 *
 * The same annuity as BR-6, seen from the other side — money received now
 * against instalments paid later — so it reuses {@link InstalmentCalculator}'s
 * solver rather than carrying a second copy. A second copy would be a second
 * thing to keep in step with docs/business-rule-vectors.md, and the two would
 * eventually disagree.
 *
 * What BR-7 adds is the settlement figure: the instalments still to pay,
 * discounted back to today at the rate the agreement implies. Paying them all
 * at once means not paying the interest that would have accrued over the
 * remaining term, and the difference is the saving — which BR-7 requires to be
 * shown, because it is the whole reason to ask.
 */
public final class LoanCalculator {

	private LoanCalculator() {
	}

	public static LoanAnalysis analyse(LoanTerms terms) {
		BigDecimal totalRepayable =
				MoneyCalculator.multiply(terms.instalmentAmount(), terms.instalmentCount());
		BigDecimal interest = MoneyCalculator.subtract(totalRepayable, terms.principal());

		InterestSummary summary = InstalmentCalculator.summarise(totalRepayable, interest,
				terms.principal(), terms.instalmentCount(), terms.instalmentAmount(),
				terms.frequency());

		int remaining = terms.instalmentsRemaining();
		BigDecimal remainingRepayable = MoneyCalculator.multiply(terms.instalmentAmount(), remaining);
		BigDecimal settlement = settlementFigure(summary, remaining, terms.instalmentAmount());

		return new LoanAnalysis(
				summary,
				totalRepayable,
				remaining,
				remainingRepayable,
				settlement,
				MoneyCalculator.subtract(remainingRepayable, settlement),
				terms.principal(),
				totalRepayable);
	}

	/**
	 * The remaining instalments discounted to today.
	 *
	 * With no interest there is nothing to discount, so the figure is simply
	 * what is left to pay — and the saving comes out at zero, which is the
	 * truthful answer: settling an interest-free loan early gains nothing, and
	 * the UI must not imply otherwise.
	 */
	private static BigDecimal settlementFigure(InterestSummary summary, int remaining,
			BigDecimal instalmentAmount) {

		if (remaining == 0) {
			return MoneyCalculator.ZERO;
		}
		if (summary.interestFree()) {
			return MoneyCalculator.multiply(instalmentAmount, remaining);
		}

		double rate = summary.periodicRate().doubleValue();
		double present = instalmentAmount.doubleValue() * (1 - Math.pow(1 + rate, -remaining)) / rate;

		return MoneyCalculator.of(BigDecimal.valueOf(present));
	}
}
