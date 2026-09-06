package ie.budgetTracker.domain.position;

import ie.budgetTracker.domain.money.MoneyCalculator;
import java.math.BigDecimal;
import java.util.List;

/**
 * BR-1 and BR-2: where someone stands right now.
 *
 * {@code totalMoneyNow = availableNow − owed}, and it may be negative. That is
 * not an edge case to guard against — it is the figure the app exists to show
 * honestly, which is why nothing here floors it at zero.
 *
 * BR-2 is the subtle half. Borrowing raises what is available by the principal
 * and what is owed by the whole repayment, so the lasting effect on the total
 * is exactly the interest. A loan is never income and never appears in the
 * earnings breakdown; the {@code borrowed} figure exists only so a screen can
 * say how much of what is available was borrowed rather than earned.
 */
public final class PositionCalculator {

	private PositionCalculator() {
	}

	public static Position calculate(PositionInputs inputs) {
		BigDecimal inAccounts = MoneyCalculator.sum(inputs.accounts().stream()
				// BR-13: an account out of totals is out of every one of them.
				.filter(AccountBalance::includeInTotals)
				.map(AccountBalance::balance)
				.toList());

		// BR-1 adds loan principals as their own term, so money borrowed but not
		// yet spent still shows as money you have. BR-2 then puts the whole
		// repayment on the other side, and the two cancel down to the interest.
		BigDecimal borrowed = MoneyCalculator.of(inputs.borrowed());
		BigDecimal available = MoneyCalculator.add(inAccounts, borrowed);

		BigDecimal onCards = MoneyCalculator.of(inputs.cardBalances());
		BigDecimal onInstalments = remaining(inputs.instalmentPlans());
		BigDecimal onLoans = remaining(inputs.loans());
		BigDecimal owed = MoneyCalculator.sum(List.of(onCards, onInstalments, onLoans));

		return new Position(
				MoneyCalculator.subtract(available, owed),
				available,
				owed,
				onCards,
				onInstalments,
				onLoans,
				borrowed);
	}

	/**
	 * BR-3: what these commitments cost in a month, averaged.
	 *
	 * This is the one place the 52/12 factor is the right answer. A derived row
	 * is a standing commitment, not a schedule of real dates, and the question
	 * it answers is "what does this cost me a month" rather than "what falls due
	 * in August". Category plans use PlanNormaliser instead, which counts real
	 * dates — see CLAUDE.md gotcha 5.
	 */
	public static BigDecimal monthlyCommitment(List<OutstandingCommitment> commitments) {
		return MoneyCalculator.sum(commitments.stream()
				.filter(commitment -> commitment.instalmentsRemaining() > 0)
				.map(commitment -> MoneyCalculator.multiply(
						commitment.instalmentAmount(), commitment.frequency().periodsPerMonth()))
				.toList());
	}

	/**
	 * The remaining instalments at face value.
	 *
	 * Undiscounted, deliberately: what is owed is what will actually be paid.
	 * Discounting is BR-7's settlement figure, which answers the different
	 * question of what it would take to clear the debt today.
	 */
	private static BigDecimal remaining(List<OutstandingCommitment> commitments) {
		return MoneyCalculator.sum(commitments.stream()
				.map(commitment -> MoneyCalculator.multiply(
						commitment.instalmentAmount(), commitment.instalmentsRemaining()))
				.toList());
	}
}
