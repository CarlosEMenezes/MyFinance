package ie.budgetTracker.domain.cards;

import java.util.UUID;

/**
 * A card, which is either a credit card or a debit card and never something in
 * between (BR-4, BR-5).
 *
 * Sealed, and the two kinds carry different components, so BR-5 is enforced by
 * the type rather than by a rule somebody has to remember: a
 * {@link DebitCard} has no closing day to set and a {@link CreditCard} cannot
 * be built without its cycle. This is the same shape the frontend's
 * `CreateCardRequest` union takes, for the same reason.
 */
public sealed interface Card permits CreditCard, DebitCard {

	UUID id();

	String name();

	/** BR-5: where the money actually comes from, for both kinds. */
	UUID accountId();

	CardKind kind();
}
