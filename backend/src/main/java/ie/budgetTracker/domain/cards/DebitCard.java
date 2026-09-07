package ie.budgetTracker.domain.cards;

import java.util.UUID;

/**
 * A debit card (BR-5): spend leaves the assigned account the same day.
 *
 * There is deliberately nowhere here to put a closing day, a due day or a
 * limit. A debit card is not a credit card with the cycle fields left empty -
 * it has no cycle, and a shape that could hold one would invite code that asks
 * what it is.
 */
public record DebitCard(UUID id, String name, UUID accountId) implements Card {

	@Override
	public CardKind kind() {
		return CardKind.DEBIT;
	}
}
