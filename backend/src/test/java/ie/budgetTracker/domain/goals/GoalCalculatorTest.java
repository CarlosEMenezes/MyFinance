package ie.budgetTracker.domain.goals;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BR-11. Every figure is a row of docs/business-rule-vectors.md, asserted
 * identically in frontend/src/lib/goals.test.ts.
 */
class GoalCalculatorTest {

	@Nested
	@DisplayName("the horizon")
	class Horizon {

		@Test
		void multipliesMonthsOutRatherThanCountingCalendarDates() {
			// Deliberately NOT BR-10's real-date counting. A goal is a smooth
			// target, not a schedule: there is no anchor date it lands on.
			assertThat(GoalCalculator.periodsUntilTarget(ContributionFrequency.MONTHLY, 4).doubleValue())
					.isCloseTo(4, within(1e-10));
			assertThat(GoalCalculator.periodsUntilTarget(ContributionFrequency.WEEKLY, 4).doubleValue())
					.isCloseTo(17.32, within(1e-10));
			assertThat(GoalCalculator.periodsUntilTarget(ContributionFrequency.DAILY, 4).doubleValue())
					.isCloseTo(121.6, within(1e-10));
		}
	}

	@Nested
	@DisplayName("the gap")
	class Gap {

		@Test
		void isWhatIsLeftToSave() {
			assertThat(GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, 4).gap())
					.isEqualTo(of("939.00"));
		}

		@Test
		void isZeroOnceTheGoalIsReachedNeverNegative() {
			GoalPlan plan = GoalCalculator.plan(of("1000"), of("1200"),
					ContributionFrequency.MONTHLY, 4);

			assertThat(plan.gap()).isEqualTo(of("0.00"));
			assertThat(plan.contributionPerPeriod()).isEqualTo(of("0.00"));
		}
	}

	@Nested
	@DisplayName("the required contribution")
	class RequiredContribution {

		@ParameterizedTest(name = "{0} over 4 months needs {1} each time")
		@CsvSource({
				"MONTHLY, 234.75",
				"WEEKLY,   54.21",
				"DAILY,     7.72",
		})
		void dividesTheGapAcrossTheHorizon(ContributionFrequency frequency, String expected) {
			assertThat(GoalCalculator.plan(of("1349"), of("410"), frequency, 4).contributionPerPeriod())
					.isEqualTo(of(expected));
		}

		@Test
		void alwaysStatesThePerMonthEquivalentWhateverTheFrequency() {
			// So two goals saving on different rhythms stay comparable.
			assertThat(GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.WEEKLY, 4)
					.monthlyRequirement()).isEqualTo(of("234.75"));
			assertThat(GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, 4)
					.monthlyRequirement()).isEqualTo(of("234.75"));
		}

		@Test
		void fallsAsTheTargetDateIsPushedOut() {
			GoalPlan soon = GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, 4);
			GoalPlan later = GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, 12);

			assertThat(soon.contributionPerPeriod()).isEqualTo(of("234.75"));
			assertThat(later.contributionPerPeriod()).isEqualTo(of("78.25"));
		}

		@ParameterizedTest(name = "{0}: {1} of {2} over {3} months needs {4}")
		@CsvSource({
				"Emergency fund,   640.00, 2000.00, 10, 136.00",
				"Interrail summer,  80.00,  900.00,  9,  91.11",
		})
		void handlesTheOtherPrototypeGoals(String name, String saved, String target, int months,
				String expected) {
			assertThat(GoalCalculator.plan(of(target), of(saved), ContributionFrequency.MONTHLY, months)
					.contributionPerPeriod()).as(name).isEqualTo(of(expected));
		}
	}

	@Nested
	@DisplayName("progress")
	class Progress {

		@ParameterizedTest(name = "{0} of {1} is {2}%")
		@CsvSource({
				" 410.00, 1349.00, 30",
				" 640.00, 2000.00, 32",
				"  80.00,  900.00,  9",
				"   0.00, 1000.00,  0",
		})
		void reportsHowMuchOfTheTargetIsSaved(String saved, String target, int expected) {
			assertThat(GoalCalculator.progressPercent(of(target), of(saved))).isEqualTo(expected);
		}

		@Test
		void isClampedToAHundredWhenOverSaved() {
			assertThat(GoalCalculator.progressPercent(of("1000"), of("1500"))).isEqualTo(100);
		}

		@Test
		void isZeroForAZeroTargetRatherThanDividingByIt() {
			assertThat(GoalCalculator.progressPercent(of("0"), of("50"))).isZero();
		}

		@Test
		void isZeroRatherThanNegativeWhenTheBalanceIsNegative() {
			assertThat(GoalCalculator.progressPercent(of("1000"), of("-50"))).isZero();
		}
	}

	@Nested
	@DisplayName("feasibility against the whole plan")
	class FeasibilityAgainstTheWholePlan {

		@Test
		void isFeasibleWhenTheMonthlyRequirementFitsInTheSpare() {
			// BR-11 compares against planned in minus planned out, which is a
			// property of the whole plan and not of the goal.
			Feasibility feasibility = GoalCalculator.assess(of("234.75"), of("400"));

			assertThat(feasibility.achievable()).isTrue();
			assertThat(feasibility.surplus()).isEqualTo(of("165.25"));
		}

		@Test
		void isNotFeasibleWhenItDoesNotAndStatesTheShortfall() {
			Feasibility feasibility = GoalCalculator.assess(of("234.75"), of("150"));

			assertThat(feasibility.achievable()).isFalse();
			assertThat(feasibility.surplus()).isEqualTo(of("-84.75"));
		}

		@Test
		void isFeasibleWithNothingToSpareWhenTheyMatchExactly() {
			Feasibility feasibility = GoalCalculator.assess(of("234.75"), of("234.75"));

			assertThat(feasibility.achievable()).isTrue();
			assertThat(feasibility.surplus()).isEqualTo(of("0.00"));
		}

		@Test
		void isNotFeasibleWhenThePlanAlreadySpendsMoreThanItEarns() {
			Feasibility feasibility = GoalCalculator.assess(of("50"), of("-120"));

			assertThat(feasibility.achievable()).isFalse();
			assertThat(feasibility.surplus()).isEqualTo(of("-170.00"));
		}
	}

	@Nested
	@DisplayName("invalid terms")
	class InvalidTerms {

		@Test
		void rejectsATargetThatIsNotInTheFuture() {
			assertThatThrownBy(
					() -> GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, 0))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("months");
			assertThatThrownBy(
					() -> GoalCalculator.plan(of("1349"), of("410"), ContributionFrequency.MONTHLY, -2))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("months");
		}

		@Test
		void rejectsANonPositiveTargetAmount() {
			assertThatThrownBy(
					() -> GoalCalculator.plan(of("0"), of("0"), ContributionFrequency.MONTHLY, 4))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("target");
		}
	}
}
