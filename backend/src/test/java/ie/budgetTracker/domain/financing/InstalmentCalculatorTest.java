package ie.budgetTracker.domain.financing;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BR-6. Every figure here is a row of docs/business-rule-vectors.md, asserted
 * identically in frontend/src/lib/instalments.test.ts.
 *
 * The rate tolerances are the same as the TypeScript side's: 8 decimal places
 * on a periodic rate, 6 on an APR. Anything looser would let the two
 * implementations drift without failing.
 */
class InstalmentCalculatorTest {

	/** The three plans the design prototype ships with. */
	private static final InstalmentTerms LAPTOP_REPAIR =
			new InstalmentTerms(of("399"), 6, of("71.50"), Frequency.MONTHLY);

	private static final InstalmentTerms FLIGHT =
			new InstalmentTerms(of("184"), 3, of("61.34"), Frequency.MONTHLY);

	private static final InstalmentTerms DESK_CHAIR =
			new InstalmentTerms(of("540"), 12, of("52.90"), Frequency.MONTHLY);

	@Nested
	@DisplayName("the totals")
	class Totals {

		@Test
		void financesTheInstalmentAmountTimesTheCount() {
			assertThat(InstalmentCalculator.analyse(LAPTOP_REPAIR).financedTotal())
					.isEqualTo(of("429.00"));
		}

		@Test
		void chargesTheDifferenceBetweenTheFinancedTotalAndTheCashPrice() {
			assertThat(InstalmentCalculator.analyse(LAPTOP_REPAIR).interest()).isEqualTo(of("30.00"));
		}
	}

	@Nested
	@DisplayName("the interest-free tolerance")
	class InterestFreeTolerance {

		@Test
		void treatsAPlanWhoseInstalmentsSumExactlyToThePriceAsInterestFree() {
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("600"), 6, of("100"), Frequency.MONTHLY));

			assertThat(summary.interestFree()).isTrue();
			assertThat(summary.periodicRate()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(summary.formatAnnualRate()).isEqualTo("0%");
		}

		@Test
		void absorbsRoundingOfOneCentPerInstalmentRatherThanSolvingOnNoise() {
			// 3 x 61.34 = 184.02 against a 184.00 price. Two cents against a
			// three-cent tolerance, so the solver is not run at all.
			InterestSummary summary = InstalmentCalculator.analyse(FLIGHT);

			assertThat(summary.interest()).isEqualTo(of("0.02"));
			assertThat(summary.interestFree()).isTrue();
			assertThat(summary.formatAnnualRate()).isEqualTo("0%");
		}

		@Test
		void isInterestFreeExactlyAtTheTolerance() {
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("120.00"), 6, of("20.01"), Frequency.MONTHLY));

			assertThat(summary.interest()).isEqualTo(of("0.06"));
			assertThat(summary.interestFree()).isTrue();
		}

		@Test
		void chargesInterestOneCentPastTheTolerance() {
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("120.00"), 6, of("20.02"), Frequency.MONTHLY));

			assertThat(summary.interest()).isEqualTo(of("0.12"));
			assertThat(summary.interestFree()).isFalse();
			assertThat(summary.periodicRate()).isGreaterThan(BigDecimal.ZERO);
		}

		@Test
		void treatsInstalmentsTotallingLessThanThePriceAsInterestFree() {
			// A discount is not negative interest, and must not reach the solver.
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("600"), 6, of("90"), Frequency.MONTHLY));

			assertThat(summary.interestFree()).isTrue();
			assertThat(summary.periodicRate()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	@Nested
	@DisplayName("the solved rate")
	class SolvedRate {

		@Test
		void solvesThePeriodicRateFromTheAnnuityIdentity() {
			assertThat(InstalmentCalculator.analyse(LAPTOP_REPAIR).periodicRate().doubleValue())
					.isCloseTo(0.02111472, within(1e-8));
		}

		@Test
		void reproducesTheCashPriceWhenTheSolvedRateIsPutBackIntoTheIdentity() {
			// The defining property of BR-6: P = A x (1 - (1+i)^-n) / i.
			double rate = InstalmentCalculator.analyse(LAPTOP_REPAIR).periodicRate().doubleValue();
			double presentValue = 71.5 * (1 - Math.pow(1 + rate, -6)) / rate;

			assertThat(presentValue).isCloseTo(399, within(1e-6));
		}

		@Test
		void compoundsThePeriodicRateOverTheYearToAnApr() {
			InterestSummary summary = InstalmentCalculator.analyse(LAPTOP_REPAIR);

			assertThat(summary.annualRate().doubleValue()).isCloseTo(0.284974, within(1e-6));
			assertThat(summary.formatAnnualRate()).isEqualTo("28.5%");
		}

		@Test
		void handlesALongerPlan() {
			InterestSummary summary = InstalmentCalculator.analyse(DESK_CHAIR);

			assertThat(summary.interest()).isEqualTo(of("94.80"));
			assertThat(summary.periodicRate().doubleValue()).isCloseTo(0.0258051, within(1e-7));
			assertThat(summary.formatAnnualRate()).isEqualTo("35.8%");
		}

		@Test
		void compoundsAWeeklyPlanFiftyTwoTimesNotTwelve() {
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("500"), 10, of("55"), Frequency.WEEKLY));

			assertThat(summary.periodicRate().doubleValue()).isCloseTo(0.01771543, within(1e-8));
			assertThat(summary.formatAnnualRate()).isEqualTo("149.2%");
		}

		@Test
		void compoundsAFortnightlyPlanTwentySixTimes() {
			InstalmentTerms fortnightly =
					new InstalmentTerms(of("500"), 10, of("55"), Frequency.FORTNIGHTLY);
			InstalmentTerms monthly = new InstalmentTerms(of("500"), 10, of("55"), Frequency.MONTHLY);

			// Same terms, same periodic rate; a shorter period compounds more often
			// and so costs more per year.
			assertThat(InstalmentCalculator.analyse(fortnightly).periodicRate().doubleValue())
					.isCloseTo(InstalmentCalculator.analyse(monthly).periodicRate().doubleValue(),
							within(1e-10));
			assertThat(InstalmentCalculator.analyse(fortnightly).annualRate())
					.isGreaterThan(InstalmentCalculator.analyse(monthly).annualRate());
		}

		@Test
		void solvesASingleInstalmentPlan() {
			// One instalment of 110 for 100 now is simply 10% for the period.
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("100"), 1, of("110"), Frequency.MONTHLY));

			assertThat(summary.periodicRate().doubleValue()).isCloseTo(0.1, within(1e-8));
		}
	}

	@Nested
	@DisplayName("the display cap")
	class DisplayCap {

		@Test
		void capsAPunitiveRateRatherThanPrintingAMeaninglessNumber() {
			InterestSummary summary = InstalmentCalculator
					.analyse(new InstalmentTerms(of("100"), 12, of("50"), Frequency.MONTHLY));

			assertThat(summary.aboveDisplayCap()).isTrue();
			assertThat(summary.formatAnnualRate()).isEqualTo(">900%");
		}

		@Test
		void doesNotCapARateBelowTheThreshold() {
			InterestSummary summary = InstalmentCalculator.analyse(DESK_CHAIR);

			assertThat(summary.aboveDisplayCap()).isFalse();
			assertThat(summary.formatAnnualRate()).isEqualTo("35.8%");
		}
	}

	@Nested
	@DisplayName("invalid terms")
	class InvalidTerms {

		@Test
		void rejectsANonPositiveInstalmentCount() {
			assertThatThrownBy(() -> new InstalmentTerms(of("399"), 0, of("71.50"), Frequency.MONTHLY))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("instalmentCount");
			assertThatThrownBy(() -> new InstalmentTerms(of("399"), -3, of("71.50"), Frequency.MONTHLY))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("instalmentCount");
		}

		@Test
		void rejectsANonPositiveCashPrice() {
			assertThatThrownBy(() -> new InstalmentTerms(of("0"), 6, of("71.50"), Frequency.MONTHLY))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("cashPrice");
		}

		@Test
		void rejectsANonPositiveInstalmentAmount() {
			assertThatThrownBy(() -> new InstalmentTerms(of("399"), 6, of("0"), Frequency.MONTHLY))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("instalmentAmount");
		}
	}
}
