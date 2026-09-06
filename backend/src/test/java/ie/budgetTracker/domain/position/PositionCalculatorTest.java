package ie.budgetTracker.domain.position;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BR-1 and BR-2, against the prototype's own accounts and financing.
 *
 * These figures have no TypeScript counterpart to drift from: the position is
 * computed server-side and rendered, never recomputed (ADR-7). What they are
 * checked against instead is the MSW fixture the frontend's Overview tests
 * assert, which is itself taken from the prototype.
 */
class PositionCalculatorTest {

	/** BR-13: the wallet, the current account and the savings, all counted. */
	private static final List<AccountBalance> ACCOUNTS = List.of(
			new AccountBalance(of("120"), true),
			new AccountBalance(of("842.30"), true),
			new AccountBalance(of("1450"), true));

	/** The prototype's three instalment plans, part-paid. */
	private static final List<OutstandingCommitment> INSTALMENTS = List.of(
			new OutstandingCommitment(of("71.50"), 4, Frequency.MONTHLY),
			new OutstandingCommitment(of("61.34"), 2, Frequency.MONTHLY),
			new OutstandingCommitment(of("52.90"), 8, Frequency.MONTHLY));

	/** The credit union and the family loan. */
	private static final List<OutstandingCommitment> LOANS = List.of(
			new OutstandingCommitment(of("118.40"), 19, Frequency.MONTHLY),
			new OutstandingCommitment(of("100"), 3, Frequency.MONTHLY));

	private static PositionInputs prototypeInputs() {
		return new PositionInputs(ACCOUNTS, of("386.40"), INSTALMENTS, LOANS, of("0"));
	}

	@Nested
	@DisplayName("what is available")
	class Available {

		@Test
		void countsOnlyTheAccountsMarkedForTotals() {
			assertThat(PositionCalculator.calculate(prototypeInputs()).availableNow())
					.isEqualTo(of("2412.30"));
		}

		@Test
		void leavesOutAnAccountExcludedFromTotals() {
			// BR-13: an account out of totals is out of every one of them.
			PositionInputs withExcluded = new PositionInputs(
					List.of(new AccountBalance(of("120"), true),
							new AccountBalance(of("5000"), false)),
					of("0"), List.of(), List.of(), of("0"));

			assertThat(PositionCalculator.calculate(withExcluded).availableNow()).isEqualTo(of("120.00"));
		}
	}

	@Nested
	@DisplayName("what is owed")
	class Owed {

		@Test
		void addsCardBalancesToRemainingInstalmentsAndLoanRepayments() {
			Position position = PositionCalculator.calculate(prototypeInputs());

			assertThat(position.owedOnCards()).isEqualTo(of("386.40"));
			assertThat(position.owedOnInstalments()).isEqualTo(of("831.88"));
			assertThat(position.owedOnLoans()).isEqualTo(of("2549.60"));
			assertThat(position.owed()).isEqualTo(of("3767.88"));
		}

		@Test
		void countsRemainingInstalmentsAtFaceValueNotDiscounted() {
			// What is owed is what will actually be paid. Discounting belongs to
			// BR-7's settlement figure, which answers a different question.
			PositionInputs oneLoan = new PositionInputs(List.of(), of("0"), List.of(),
					List.of(new OutstandingCommitment(of("118.40"), 19, Frequency.MONTHLY)), of("0"));

			assertThat(PositionCalculator.calculate(oneLoan).owed()).isEqualTo(of("2249.60"));
		}
	}

	@Nested
	@DisplayName("the total")
	class Total {

		@Test
		void isAvailableMinusOwedAndMayBeNegative() {
			// BR-1 says so outright, and the screen shows it in red.
			assertThat(PositionCalculator.calculate(prototypeInputs()).totalMoneyNow())
					.isEqualTo(of("-1355.58"));
		}

		@Test
		void isTheSumOfNothingWhenThereIsNothing() {
			PositionInputs empty =
					new PositionInputs(List.of(), of("0"), List.of(), List.of(), of("0"));
			Position position = PositionCalculator.calculate(empty);

			assertThat(position.availableNow()).isEqualTo(of("0.00"));
			assertThat(position.owed()).isEqualTo(of("0.00"));
			assertThat(position.totalMoneyNow()).isEqualTo(of("0.00"));
		}
	}

	@Nested
	@DisplayName("BR-2, borrowing moves both sides")
	class Borrowing {

		@Test
		void raisesAvailableByThePrincipalAndOwedByTheWholeRepayment() {
			// A 2,500 loan repaid as 24 x 118.40.
			PositionInputs borrowed = new PositionInputs(
					List.of(new AccountBalance(of("120"), true)),
					of("0"), List.of(),
					List.of(new OutstandingCommitment(of("118.40"), 24, Frequency.MONTHLY)),
					of("2500"));
			Position position = PositionCalculator.calculate(borrowed);

			assertThat(position.borrowed()).isEqualTo(of("2500.00"));
			assertThat(position.availableNow()).isEqualTo(of("2620.00"));
			assertThat(position.owed()).isEqualTo(of("2841.60"));
		}

		@Test
		void nettsOutToExactlyTheInterestAndNothingElse() {
			// BR-2's whole point: borrowing is not income, and the only lasting
			// effect on the position is what the borrowing costs.
			PositionInputs before = new PositionInputs(
					List.of(new AccountBalance(of("120"), true)), of("0"), List.of(), List.of(), of("0"));
			PositionInputs after = new PositionInputs(
					List.of(new AccountBalance(of("120"), true)),
					of("0"), List.of(),
					List.of(new OutstandingCommitment(of("118.40"), 24, Frequency.MONTHLY)),
					of("2500"));

			var difference = PositionCalculator.calculate(before).totalMoneyNow()
					.subtract(PositionCalculator.calculate(after).totalMoneyNow());

			// 24 x 118.40 - 2500 = 341.60, the interest and nothing else.
			assertThat(difference).isEqualTo(of("341.60"));
		}
	}

	@Nested
	@DisplayName("BR-3, the derived plan rows")
	class DerivedRows {

		@Test
		void smoothsEachCommitmentIntoAMonthlyFigure() {
			// Monthly commitments, so the 52/12 average leaves them unchanged and
			// the row is simply what is paid each month.
			assertThat(PositionCalculator.monthlyCommitment(LOANS)).isEqualTo(of("218.40"));
			assertThat(PositionCalculator.monthlyCommitment(INSTALMENTS)).isEqualTo(of("185.74"));
		}

		@Test
		void usesTheAveragedFactorNotARealDateCount() {
			// BR-3's narrow exception: a weekly repayment of 50.00 is 216.67 a month
			// on the 52/12 average, whatever a particular month actually holds.
			var weekly = List.of(new OutstandingCommitment(of("50"), 10, Frequency.WEEKLY));

			assertThat(PositionCalculator.monthlyCommitment(weekly)).isEqualTo(of("216.67"));
		}

		@Test
		void countsNothingOnceACommitmentIsFullyPaid() {
			var settled = List.of(new OutstandingCommitment(of("118.40"), 0, Frequency.MONTHLY));

			assertThat(PositionCalculator.monthlyCommitment(settled)).isEqualTo(of("0.00"));
		}
	}
}
