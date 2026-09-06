package ie.budgetTracker.domain.financing;

import java.math.BigDecimal;

/**
 * What a loan costs, what is still owed on it, and what settling it now saves
 * (BR-7), plus what it does to the position (BR-2).
 */
public record LoanAnalysis(
		InterestSummary interest,
		BigDecimal totalRepayable,
		int instalmentsRemaining,
		/** The remaining instalments at face value, undiscounted. */
		BigDecimal remainingRepayable,
		/** Those instalments discounted back to today at the solved rate. */
		BigDecimal settlementFigureToday,
		/**
		 * {@code remainingRepayable - settlementFigureToday}. BR-7 requires this
		 * to be shown, which is why it is computed here rather than left to a
		 * screen to subtract for itself.
		 */
		BigDecimal earlyPayoffSaving,
		/** BR-2: the principal. */
		BigDecimal addsToAvailable,
		/** BR-2: the whole repayment, so the net effect is the interest. */
		BigDecimal addsToOwed) {
}
