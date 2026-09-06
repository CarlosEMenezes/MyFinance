package ie.budgetTracker.domain.position;

import ie.budgetTracker.domain.plan.Frequency;
import java.math.BigDecimal;

/**
 * Instalments still to pay on a card plan or a loan (BR-1, BR-3).
 *
 * Only what remains: the instalments already paid are gone and are not owed.
 */
public record OutstandingCommitment(
		BigDecimal instalmentAmount,
		int instalmentsRemaining,
		Frequency frequency) {

	public OutstandingCommitment {
		if (instalmentsRemaining < 0) {
			throw new IllegalArgumentException(
					"instalmentsRemaining must not be negative, but was " + instalmentsRemaining);
		}
	}
}
