package ie.budgetTracker.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import ie.budgetTracker.domain.money.MoneyCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two ways a frequency turns into a count, and why they must not be
 * confused (CLAUDE.md gotcha 5, docs/business-rule-vectors.md).
 */
class FrequencyTest {

	@Nested
	@DisplayName("BR-6, compounding an APR")
	class PeriodsPerYear {

		@Test
		void countsTheCompoundingPeriodsInAYear() {
			assertThat(Frequency.WEEKLY.periodsPerYear()).isEqualTo(52);
			assertThat(Frequency.FORTNIGHTLY.periodsPerYear()).isEqualTo(26);
			assertThat(Frequency.MONTHLY.periodsPerYear()).isEqualTo(12);
		}

		@Test
		void isDefinedForEveryMemberSoTheAprConversionCannotBeHandedAnUndefinedOne() {
			// BR-17: this is exactly why recurrence rules get their own vocabulary.
			// A DAILY or YEARLY member here would make the APR meaningless.
			for (Frequency frequency : Frequency.values()) {
				assertThat(frequency.periodsPerYear()).isPositive();
			}
		}
	}

	@Nested
	@DisplayName("BR-3, the averaged monthly commitment")
	class PeriodsPerMonth {

		@Test
		void averagesWeeklyAndFortnightlyOverAYearAndLeavesMonthlyAtOne() {
			assertThat(Frequency.WEEKLY.periodsPerMonth().doubleValue())
					.isCloseTo(52.0 / 12, within(1e-10));
			assertThat(Frequency.FORTNIGHTLY.periodsPerMonth().doubleValue())
					.isCloseTo(26.0 / 12, within(1e-10));
			assertThat(Frequency.MONTHLY.periodsPerMonth()).isEqualByComparingTo(BigDecimal.ONE);
		}

		@Test
		void smoothsAWeeklyCommitmentToItsMonthlyEquivalent() {
			// The BR-3 vector: 160.00 a week smooths to 693.33 a month. Correct as a
			// commitment average; wrong as a month's real cost, which BR-10 counts
			// from real dates instead.
			BigDecimal perMonth = MoneyCalculator.multiply(
					MoneyCalculator.of("160"), Frequency.WEEKLY.periodsPerMonth());

			assertThat(perMonth).isEqualTo(MoneyCalculator.of("693.33"));
		}

		@Test
		void isNotTheSameAsCountingRealDates() {
			// August 2026 holds five weekly paydays; the average says 4.33. Both are
			// right in their place, and unifying them would break one of BR-3 or
			// BR-10 without touching the other's tests.
			assertThat(Frequency.WEEKLY.periodsPerMonth().doubleValue()).isLessThan(5);
		}
	}
}
