package ie.budgetTracker.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-6 and docs/business-rule-vectors.md. Every rounding vector here has a
 * counterpart in frontend/src/lib/money.test.ts asserting the same result; the
 * two must never disagree.
 */
class MoneyCalculatorTest {

	@Nested
	@DisplayName("scale and rounding")
	class ScaleAndRounding {

		@Test
		void amountIsAlwaysHeldAtTwoDecimalPlaces() {
			assertThat(MoneyCalculator.of("74.2")).isEqualTo(new BigDecimal("74.20"));
			assertThat(MoneyCalculator.of("100")).isEqualTo(new BigDecimal("100.00"));
		}

		@Test
		void halfIsRoundedAwayFromZero() {
			// HALF_UP, matching the TypeScript side's digit-string parser. HALF_EVEN
			// would give 0.00, and Math.round in JS would disagree on the negative.
			assertThat(MoneyCalculator.of("0.005")).isEqualTo(new BigDecimal("0.01"));
			assertThat(MoneyCalculator.of("-0.005")).isEqualTo(new BigDecimal("-0.01"));
		}

		@Test
		void roundingIsSymmetricAboutZero() {
			assertThat(MoneyCalculator.of("2.345")).isEqualTo(new BigDecimal("2.35"));
			assertThat(MoneyCalculator.of("-2.345")).isEqualTo(new BigDecimal("-2.35"));
		}

		@Test
		void rejectsTextThatIsNotAnAmount() {
			assertThatThrownBy(() -> MoneyCalculator.of("twelve"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("amount");
		}

		@Test
		void rejectsAHalfTypedAmount() {
			assertThatThrownBy(() -> MoneyCalculator.of("12."))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("arithmetic")
	class Arithmetic {

		@Test
		void addsAtScaleTwo() {
			assertThat(MoneyCalculator.add(MoneyCalculator.of("71.50"), MoneyCalculator.of("0.005")))
					.isEqualTo(new BigDecimal("71.51"));
		}

		@Test
		void subtractsAtScaleTwo() {
			assertThat(MoneyCalculator.subtract(MoneyCalculator.of("429"), MoneyCalculator.of("399")))
					.isEqualTo(new BigDecimal("30.00"));
		}

		@Test
		void multipliesByACount() {
			// BR-6: financedTotal = A x n.
			assertThat(MoneyCalculator.multiply(MoneyCalculator.of("71.50"), 6))
					.isEqualTo(new BigDecimal("429.00"));
		}

		@Test
		void multipliesByAFactorAndRoundsOnce() {
			// BR-3: 160.00 x 52/12 smooths to 693.33, not 693.3333...
			BigDecimal weekly = MoneyCalculator.of("160");
			BigDecimal perMonth = MoneyCalculator.multiply(weekly, new BigDecimal("52")
					.divide(new BigDecimal("12"), MoneyCalculator.RATE_CONTEXT));

			assertThat(perMonth).isEqualTo(new BigDecimal("693.33"));
		}

		@Test
		void dividesAndRoundsHalfUp() {
			// BR-11: 939.00 across 4 months.
			assertThat(MoneyCalculator.divide(MoneyCalculator.of("939"), new BigDecimal("4")))
					.isEqualTo(new BigDecimal("234.75"));
		}

		@Test
		void sumsAList() {
			assertThat(MoneyCalculator.sum(List.of(
					MoneyCalculator.of("120"),
					MoneyCalculator.of("842.30"),
					MoneyCalculator.of("1450"))))
					.isEqualTo(new BigDecimal("2412.30"));
		}

		@Test
		void sumsNothingToZero() {
			assertThat(MoneyCalculator.sum(List.of())).isEqualTo(MoneyCalculator.ZERO);
		}

		@Test
		void zeroIsHeldAtScaleTwoSoItComparesEqualToOtherAmounts() {
			assertThat(MoneyCalculator.ZERO).isEqualTo(new BigDecimal("0.00"));
		}
	}

	@Nested
	@DisplayName("comparison")
	class Comparison {

		@Test
		void comparesByValueNotByScale() {
			// BigDecimal.equals distinguishes 0.0 from 0.00; this must not.
			assertThat(MoneyCalculator.isZero(new BigDecimal("0.0"))).isTrue();
			assertThat(MoneyCalculator.isZero(MoneyCalculator.ZERO)).isTrue();
			assertThat(MoneyCalculator.isZero(MoneyCalculator.of("0.01"))).isFalse();
		}

		@Test
		void recognisesANegativeAmount() {
			// BR-1: totalMoneyNow may be negative and is shown in red.
			assertThat(MoneyCalculator.isNegative(MoneyCalculator.of("-0.01"))).isTrue();
			assertThat(MoneyCalculator.isNegative(MoneyCalculator.ZERO)).isFalse();
		}

		@Test
		void floorsAnAmountAtZero() {
			// BR-11: a reached goal has a gap of zero, never a negative one.
			assertThat(MoneyCalculator.atLeastZero(MoneyCalculator.of("-200")))
					.isEqualTo(MoneyCalculator.ZERO);
			assertThat(MoneyCalculator.atLeastZero(MoneyCalculator.of("200")))
					.isEqualTo(new BigDecimal("200.00"));
		}
	}
}
