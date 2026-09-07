package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.financing.InterestSummary;
import java.math.BigDecimal;

/**
 * What a financed purchase or a loan actually costs (BR-6, BR-7).
 *
 * `interestFree` and `aboveDisplayCap` are sent rather than left to be
 * inferred from the rates. BR-6 says an interest-free plan displays exactly
 * `0%` and anything past 900% displays as a bound, and a screen that decided
 * that for itself by comparing numbers would eventually decide it differently
 * from the solver that produced them.
 *
 * The rates are `BigDecimal` and reach the wire as JSON numbers. A `double`
 * would carry its binary representation across (spec §0.5).
 */
public record InterestSummaryResponse(
		long financedTotal,
		long interest,
		boolean interestFree,
		/** Per instalment period, as a fraction. Zero when interest free. */
		BigDecimal periodicRate,
		/** Compounded over the year, as a fraction. Zero when interest free. */
		BigDecimal annualRate,
		boolean aboveDisplayCap) {

	public static InterestSummaryResponse from(InterestSummary summary) {
		return new InterestSummaryResponse(
				Money.toMinorUnits(summary.financedTotal()),
				Money.toMinorUnits(summary.interest()),
				summary.interestFree(),
				summary.periodicRate(),
				summary.annualRate(),
				summary.aboveDisplayCap());
	}
}
