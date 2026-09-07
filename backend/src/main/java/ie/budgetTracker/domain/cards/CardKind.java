package ie.budgetTracker.domain.cards;

/**
 * What a card is, which decides whether it has a cycle at all.
 *
 * BR-4 belongs to CREDIT and BR-5 to DEBIT, and the two are not variations of
 * one another: debit spend leaves the account the same day, so there is no
 * statement for it to join.
 */
public enum CardKind {
	CREDIT,
	DEBIT
}
