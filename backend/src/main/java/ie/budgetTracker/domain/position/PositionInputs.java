package ie.budgetTracker.domain.position;

import java.math.BigDecimal;
import java.util.List;

/** Everything BR-1 needs to state where someone stands right now. */
public record PositionInputs(
		List<AccountBalance> accounts,
		/** What is currently sitting on credit cards. */
		BigDecimal cardBalances,
		List<OutstandingCommitment> instalmentPlans,
		List<OutstandingCommitment> loans,
		/**
		 * BR-2: loan principals received and logged here.
		 *
		 * BR-1 adds this to what is available as its own term, so a loan taken but
		 * not yet spent still shows as money you have. It is named separately
		 * rather than folded in so a screen can say how much of what is available
		 * was borrowed rather than earned - which is the difference between a
		 * comfortable month and a debt.
		 */
		BigDecimal borrowed) {
}
