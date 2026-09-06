package ie.budgetTracker.domain.financing;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BR-7. Every figure is a row of docs/business-rule-vectors.md, asserted
 * identically in frontend/src/lib/loans.test.ts.
 */
class LoanCalculatorTest {

	/** The two loans the design prototype ships with. */
	private static LoanTerms creditUnion(int instalmentsPaid) {
		return new LoanTerms(of("2500"), 24, of("118.40"), Frequency.MONTHLY, instalmentsPaid);
	}

	private static final LoanTerms FAMILY_LOAN =
			new LoanTerms(of("600"), 6, of("100"), Frequency.MONTHLY, 3);

	@Nested
	@DisplayName("the cost of borrowing")
	class CostOfBorrowing {

		@Test
		void repaysTheInstalmentAmountTimesTheCount() {
			assertThat(LoanCalculator.analyse(creditUnion(5)).totalRepayable()).isEqualTo(of("2841.60"));
		}

		@Test
		void chargesTheDifferenceBetweenWhatIsRepaidAndWhatWasReceived() {
			assertThat(LoanCalculator.analyse(creditUnion(5)).interest().interest())
					.isEqualTo(of("341.60"));
		}

		@Test
		void solvesTheSameRateAnInstalmentPlanOnThoseTermsWould() {
			// BR-7 is BR-6 seen from the other side, and reuses its solver rather
			// than growing a second copy of it.
			InterestSummary summary = LoanCalculator.analyse(creditUnion(5)).interest();

			assertThat(summary.periodicRate().doubleValue()).isCloseTo(0.01051039, within(1e-8));
			assertThat(summary.formatAnnualRate()).isEqualTo("13.4%");
		}

		@Test
		void recognisesAnInterestFreeLoanFromAFamilyMember() {
			InterestSummary summary = LoanCalculator.analyse(FAMILY_LOAN).interest();

			assertThat(summary.interest()).isEqualTo(of("0.00"));
			assertThat(summary.interestFree()).isTrue();
			assertThat(summary.formatAnnualRate()).isEqualTo("0%");
		}
	}

	@Nested
	@DisplayName("what is still owed")
	class StillOwed {

		@Test
		void countsTheInstalmentsStillToPay() {
			assertThat(LoanCalculator.analyse(creditUnion(5)).instalmentsRemaining()).isEqualTo(19);
		}

		@Test
		void owesTheRemainingInstalmentsAtFaceValue() {
			assertThat(LoanCalculator.analyse(creditUnion(5)).remainingRepayable())
					.isEqualTo(of("2249.60"));
		}
	}

	@Nested
	@DisplayName("settling early")
	class SettlingEarly {

		@Test
		void discountsTheRemainingInstalmentsBackToToday() {
			assertThat(LoanCalculator.analyse(creditUnion(5)).settlementFigureToday())
					.isEqualTo(of("2029.59"));
		}

		@Test
		void savesTheInterestThatWouldHaveAccruedOnTheRemainingTerm() {
			// BR-7 requires this figure to be shown, so it is computed here rather
			// than left to a screen to subtract.
			assertThat(LoanCalculator.analyse(creditUnion(5)).earlyPayoffSaving())
					.isEqualTo(of("220.01"));
		}

		@Test
		void settlesAnUntouchedLoanAtExactlyThePrincipalSavingExactlyTheInterest() {
			LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(0));

			assertThat(analysis.settlementFigureToday()).isEqualTo(of("2500.00"));
			assertThat(analysis.earlyPayoffSaving()).isEqualTo(of("341.60"));
			assertThat(analysis.earlyPayoffSaving()).isEqualTo(analysis.interest().interest());
		}

		@Test
		void discountsASingleRemainingInstalmentByOnePeriod() {
			LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(23));

			assertThat(analysis.instalmentsRemaining()).isEqualTo(1);
			assertThat(analysis.settlementFigureToday()).isEqualTo(of("117.17"));
			assertThat(analysis.earlyPayoffSaving()).isEqualTo(of("1.23"));
		}

		@Test
		void gainsNothingBySettlingAnInterestFreeLoanEarly() {
			LoanAnalysis analysis = LoanCalculator.analyse(FAMILY_LOAN);

			assertThat(analysis.remainingRepayable()).isEqualTo(of("300.00"));
			assertThat(analysis.settlementFigureToday()).isEqualTo(of("300.00"));
			assertThat(analysis.earlyPayoffSaving()).isEqualTo(of("0.00"));
		}

		@Test
		void owesAndSavesNothingOnceEveryInstalmentIsPaid() {
			LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(24));

			assertThat(analysis.instalmentsRemaining()).isZero();
			assertThat(analysis.remainingRepayable()).isEqualTo(of("0.00"));
			assertThat(analysis.settlementFigureToday()).isEqualTo(of("0.00"));
			assertThat(analysis.earlyPayoffSaving()).isEqualTo(of("0.00"));
		}

		@Test
		void neverClaimsASavingLargerThanTheInterestItself() {
			for (int paid = 0; paid <= 24; paid++) {
				LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(paid));

				assertThat(analysis.earlyPayoffSaving())
						.as("saving after %d instalments", paid)
						.isLessThanOrEqualTo(analysis.interest().interest());
			}
		}
	}

	@Nested
	@DisplayName("invalid terms")
	class InvalidTerms {

		@Test
		void rejectsANegativeNumberOfInstalmentsPaid() {
			assertThatThrownBy(() -> creditUnion(-1))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("instalmentsPaid");
		}

		@Test
		void rejectsMoreInstalmentsPaidThanTheLoanHas() {
			assertThatThrownBy(() -> creditUnion(25))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("instalmentsPaid");
		}

		@Test
		void rejectsANonPositivePrincipal() {
			assertThatThrownBy(
					() -> new LoanTerms(of("0"), 24, of("118.40"), Frequency.MONTHLY, 5))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("principal");
		}
	}

	@Nested
	@DisplayName("BR-2, what borrowing does to the position")
	class EffectOnPosition {

		@Test
		void raisesAvailableByThePrincipalAndOwedByTheWholeRepayment() {
			LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(0));

			assertThat(analysis.addsToAvailable()).isEqualTo(of("2500.00"));
			assertThat(analysis.addsToOwed()).isEqualTo(of("2841.60"));
		}

		@Test
		void nettsOutToExactlyTheInterest() {
			// BR-2: the net effect of borrowing on total money now is the interest,
			// and nothing else.
			LoanAnalysis analysis = LoanCalculator.analyse(creditUnion(0));

			assertThat(analysis.addsToOwed().subtract(analysis.addsToAvailable()))
					.isEqualTo(analysis.interest().interest());
		}
	}
}
