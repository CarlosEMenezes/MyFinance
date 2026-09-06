package ie.budgetTracker.domain.financing;

import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.plan.Frequency;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BR-6: what spreading a purchase over instalments actually costs.
 *
 * {@code financedTotal = A x n}, {@code interest = financedTotal - P}. Then, if
 * the interest is real rather than rounding, the rate implied by the annuity
 * identity {@code P = A(1 - (1+i)^-n) / i} is found by bisection and compounded
 * into an APR.
 *
 * The tolerance check comes first and that ordering is the rule, not an
 * optimisation. Three instalments of 61.34 against a price of 184.00 differ by
 * two cents, which is rounding; running the solver on it would produce a
 * confident APR from noise. BR-6 puts the band at one cent per instalment.
 */
public final class InstalmentCalculator {

	/** BR-6: one cent per instalment is rounding, not interest. */
	private static final BigDecimal TOLERANCE_PER_INSTALMENT = new BigDecimal("0.01");

	/**
	 * The bisection bracket. A periodic rate above 300% is past anything a real
	 * agreement expresses, and the display caps long before it.
	 *
	 * BigDecimal, not double, because the architecture rule forbids a
	 * floating-point *field* anywhere in this application - and it is right to.
	 * The solver converts once, on entry; the bracket itself is never state a
	 * rounding error can accumulate in.
	 */
	private static final BigDecimal LOWEST_RATE = new BigDecimal("0.000000000001");
	private static final BigDecimal HIGHEST_RATE = new BigDecimal("3");

	/**
	 * Enough iterations to exhaust the bracket's precision. Bisection halves the
	 * interval each pass, so 200 is far past the point where a double stops
	 * changing - it costs nothing and removes any question of an early exit
	 * returning a half-solved rate.
	 */
	private static final int MAX_ITERATIONS = 200;

	private InstalmentCalculator() {
	}

	public static InterestSummary analyse(InstalmentTerms terms) {
		BigDecimal financedTotal =
				MoneyCalculator.multiply(terms.instalmentAmount(), terms.instalmentCount());
		BigDecimal interest = MoneyCalculator.subtract(financedTotal, terms.cashPrice());

		return summarise(financedTotal, interest, terms.cashPrice(), terms.instalmentCount(),
				terms.instalmentAmount(), terms.frequency());
	}

	/**
	 * Shared with {@link LoanCalculator}: BR-7 is the same annuity seen from the
	 * other side, and a second copy of this would be a second thing to keep in
	 * step with docs/business-rule-vectors.md.
	 */
	static InterestSummary summarise(BigDecimal financedTotal, BigDecimal interest,
			BigDecimal presentValue, int count, BigDecimal instalmentAmount, Frequency frequency) {

		BigDecimal tolerance = TOLERANCE_PER_INSTALMENT.multiply(BigDecimal.valueOf(count));
		if (interest.compareTo(tolerance) <= 0) {
			// Covers a genuine 0% deal, rounding noise, and instalments totalling
			// less than the price - a discount is not negative interest.
			return new InterestSummary(financedTotal, interest, true,
					BigDecimal.ZERO, BigDecimal.ZERO, false);
		}

		double periodic = solvePeriodicRate(presentValue.doubleValue(), count,
				instalmentAmount.doubleValue());
		double annual = Math.pow(1 + periodic, frequency.periodsPerYear()) - 1;

		return new InterestSummary(financedTotal, interest, false,
				rate(periodic), rate(annual),
				annual > InterestSummary.DISPLAY_CAP.doubleValue());
	}

	/**
	 * Bisection on the annuity identity.
	 *
	 * The present value falls monotonically as the rate rises, which is what
	 * makes bisection valid here: at the low end the instalments are worth more
	 * than the price, at the high end less, and the crossing is the rate the
	 * agreement implies. No derivative, no starting guess, no failure to
	 * converge.
	 */
	private static double solvePeriodicRate(double presentValue, int count, double instalment) {
		double low = LOWEST_RATE.doubleValue();
		double high = HIGHEST_RATE.doubleValue();

		for (int i = 0; i < MAX_ITERATIONS; i++) {
			double mid = (low + high) / 2;
			if (presentValueAt(mid, count, instalment) > presentValue) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) / 2;
	}

	/** {@code A x (1 - (1+i)^-n) / i} - what the instalments are worth today. */
	private static double presentValueAt(double rate, int count, double instalment) {
		return instalment * (1 - Math.pow(1 + rate, -count)) / rate;
	}

	private static BigDecimal rate(double value) {
		return BigDecimal.valueOf(value).setScale(InterestSummary.RATE_SCALE, RoundingMode.HALF_UP);
	}
}
