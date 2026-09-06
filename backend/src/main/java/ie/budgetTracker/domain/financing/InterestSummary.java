package ie.budgetTracker.domain.financing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a financed purchase or a loan actually costs (BR-6, BR-7).
 *
 * The two rates are BigDecimal, not double. Partly because the architecture
 * rule forbids a floating-point field anywhere in this application, and partly
 * because these cross the wire: a rate serialised from a double carries its
 * binary representation with it, and the frontend would render that.
 *
 * The solver works in double internally, where the arithmetic is cheap and the
 * precision is far beyond what a rate needs, and converts once on the way out.
 */
public record InterestSummary(
		BigDecimal financedTotal,
		BigDecimal interest,
		boolean interestFree,
		/** Per instalment period, as a fraction. Zero when interest free. */
		BigDecimal periodicRate,
		/** Compounded over the year, as a fraction. Zero when interest free. */
		BigDecimal annualRate,
		boolean aboveDisplayCap) {

	/**
	 * BR-6: past this the figure stops telling anyone anything useful, so it is
	 * reported as a bound rather than as a number.
	 */
	public static final BigDecimal DISPLAY_CAP = new BigDecimal("9");

	/** Rates are held well past what a percentage to one decimal place needs. */
	static final int RATE_SCALE = 10;

	private static final int RATE_DECIMAL_PLACES = 1;

	/**
	 * The APR as the UI shows it: one decimal place, an exact {@code 0%} when
	 * interest free, and a bound rather than a number past the cap.
	 *
	 * Formatting lives with the figure because BR-6 specifies the display as
	 * part of the rule - "display exactly 0%", "cap at >900%" - and a caller
	 * that formatted it itself could satisfy the arithmetic and still break the
	 * rule.
	 */
	public String formatAnnualRate() {
		if (interestFree) {
			return "0%";
		}
		if (aboveDisplayCap) {
			return ">" + DISPLAY_CAP.multiply(BigDecimal.valueOf(100)).toBigInteger() + "%";
		}
		// Always one decimal place, never stripped: the TypeScript side uses
		// toFixed(1), so "30.0%" there must not be "30%" here.
		return annualRate.multiply(BigDecimal.valueOf(100))
				.setScale(RATE_DECIMAL_PLACES, RoundingMode.HALF_UP)
				.toPlainString() + "%";
	}
}
