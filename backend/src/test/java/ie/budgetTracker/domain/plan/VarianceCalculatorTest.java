package ie.budgetTracker.domain.plan;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BR-9. Every row is a row of docs/business-rule-vectors.md, asserted
 * identically in frontend/src/lib/variance.test.ts.
 */
class VarianceCalculatorTest {

	@Nested
	@DisplayName("the figure")
	class Figure {

		@ParameterizedTest(name = "{0}: planned {1} against real {2} is {3}")
		@CsvSource({
				"EARNING, 1200.00, 1010.00, -190.00",
				"EARNING,  160.00,  672.00,  512.00",
				"EXPENSE,   60.00,  318.40,  258.40",
				"EXPENSE,   45.00,   44.98,   -0.02",
				"EARNING,  450.00,  450.00,    0.00",
		})
		void isRealMinusPlannedForBothKinds(CategoryType type, String planned, String real,
				String expected) {
			// The prototype flips the sign for expenses so underspending reads
			// positive; the spec does not, and the spec wins (CLAUDE.md gotcha 18).
			assertThat(VarianceCalculator.varianceOf(type, of(planned), of(real)).amount())
					.isEqualTo(of(expected));
		}

		@Test
		void hasOneSignConventionSoAColumnOfVariancesCanBeSummed() {
			// This is what a single convention buys: adding an earnings variance to
			// an expense variance is meaningful without asking what kind each row is.
			Variance earning = VarianceCalculator.varianceOf(CategoryType.EARNING, of("160"), of("672"));
			Variance expense = VarianceCalculator.varianceOf(CategoryType.EXPENSE, of("60"), of("318.40"));

			assertThat(earning.amount().add(expense.amount())).isEqualTo(of("770.40"));
		}
	}

	@Nested
	@DisplayName("the tone")
	class Tone {

		@ParameterizedTest(name = "{0}: planned {1} against real {2} is {3}")
		@CsvSource({
				"EARNING, 1200.00, 1010.00, BAD",
				"EARNING,  160.00,  672.00, GOOD",
				"EARNING,  450.00,  450.00, NEUTRAL",
				"EARNING,    0.00,   64.50, GOOD",
				"EXPENSE,   60.00,  318.40, BAD",
				"EXPENSE,   45.00,   44.98, GOOD",
				"EXPENSE,   29.00,   29.00, NEUTRAL",
		})
		void readsOverPlanAsGoodForEarningsAndBadForExpenses(CategoryType type, String planned,
				String real, VarianceTone expected) {
			assertThat(VarianceCalculator.varianceOf(type, of(planned), of(real)).tone())
					.isEqualTo(expected);
		}

		@Test
		void isNeutralAtExactlyZeroForBothKinds() {
			// Zero is grey, never green: matching the plan is not an achievement to
			// colour, and BR-9 says so explicitly.
			assertThat(VarianceCalculator.varianceOf(CategoryType.EARNING, of("450"), of("450")).tone())
					.isEqualTo(VarianceTone.NEUTRAL);
			assertThat(VarianceCalculator.varianceOf(CategoryType.EXPENSE, of("29"), of("29")).tone())
					.isEqualTo(VarianceTone.NEUTRAL);
		}

		@Test
		void givesTheSameFigureOppositeTonesAcrossTheTwoKinds() {
			// +258.40 is bad on an expense and good on an earning. The figure does
			// not change; only what it means does.
			Variance overspent = VarianceCalculator.varianceOf(CategoryType.EXPENSE, of("60"), of("318.40"));
			Variance overearned = VarianceCalculator.varianceOf(CategoryType.EARNING, of("60"), of("318.40"));

			assertThat(overspent.amount()).isEqualTo(overearned.amount());
			assertThat(overspent.tone()).isEqualTo(VarianceTone.BAD);
			assertThat(overearned.tone()).isEqualTo(VarianceTone.GOOD);
		}
	}
}
