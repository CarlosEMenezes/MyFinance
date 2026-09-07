package ie.budgetTracker.domain.financing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A card purchase spread over instalments (BR-6).
 *
 * The terms are held; the interest is not. BR-6 solves the periodic rate from
 * the terms every time it is asked, so storing an APR beside them would be a
 * figure that was true once, sitting next to terms that have moved on.
 *
 * `firstDueDate` is BR-4's answer, not the purchase date: the first instalment
 * lands on a bill.
 */
public record InstalmentPlan(
		UUID id,
		UUID cardId,
		String label,
		InstalmentTerms terms,
		int instalmentsPaid,
		LocalDate firstDueDate) {

	public InstalmentPlan {
		if (instalmentsPaid < 0 || instalmentsPaid > terms.instalmentCount()) {
			throw new IllegalArgumentException("instalmentsPaid must be between 0 and "
					+ terms.instalmentCount() + ", but was " + instalmentsPaid);
		}
	}

	/** BR-3: what is still to be paid, which is what the derived row counts. */
	public int instalmentsRemaining() {
		return terms.instalmentCount() - instalmentsPaid;
	}
}
