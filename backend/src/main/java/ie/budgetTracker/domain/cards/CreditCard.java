package ie.budgetTracker.domain.cards;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A credit card: a limit, what is currently owed against it, and a cycle
 * (BR-4).
 *
 * The cycle is not optional. A credit card without one could not answer when
 * its spending is billed, and BR-4 says that date - never the purchase date -
 * is what the plan is built from.
 */
public record CreditCard(
		UUID id,
		String name,
		UUID accountId,
		BigDecimal creditLimit,
		/** BR-1 counts this among what is owed. */
		BigDecimal currentBalance,
		StatementCycle cycle) implements Card {

	public CreditCard {
		if (cycle == null) {
			throw new IllegalArgumentException("a credit card has a statement cycle (BR-4)");
		}
	}

	@Override
	public CardKind kind() {
		return CardKind.CREDIT;
	}
}
