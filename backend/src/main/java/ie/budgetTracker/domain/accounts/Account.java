package ie.budgetTracker.domain.accounts;

import ie.budgetTracker.domain.money.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Where money sits (BR-13).
 *
 * `balance` is the whole account, pockets included. `pockets` names parts of
 * that same balance, so summing them beside it counts the money twice - which
 * is the one dangerous misreading of this shape, and why every consumer of it
 * says so where the pockets are listed.
 */
public record Account(
		UUID id,
		String name,
		AccountKind kind,
		BigDecimal balance,
		Currency currency,
		/** BR-13: false means labelled out of totals everywhere. */
		boolean includeInTotals,
		String note,
		List<Pocket> pockets) {
}
