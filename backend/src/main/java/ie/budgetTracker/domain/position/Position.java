package ie.budgetTracker.domain.position;

import java.math.BigDecimal;

/** Where someone stands right now (BR-1, BR-2). */
public record Position(
		/** availableNow - owed. May be negative, and BR-1 says to show it in red. */
		BigDecimal totalMoneyNow,
		BigDecimal availableNow,
		BigDecimal owed,
		BigDecimal owedOnCards,
		BigDecimal owedOnInstalments,
		BigDecimal owedOnLoans,
		/** Included in availableNow; the net effect on the total is the interest. */
		BigDecimal borrowed) {
}
