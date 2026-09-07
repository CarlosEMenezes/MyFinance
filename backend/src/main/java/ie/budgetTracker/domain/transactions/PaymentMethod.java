package ie.budgetTracker.domain.transactions;

import java.util.UUID;

/**
 * Where the money for a transaction came from or went (BR-4, BR-5).
 *
 * The wire carries one `paymentMethodId` because a person picks one thing from
 * one list. What that thing *is* decides the arithmetic: BR-4 puts credit-card
 * spending on a future bill, BR-5 takes debit spending out the same day, and
 * BR-1 counts only expenses not paid by credit card. So the kind travels with
 * the id everywhere inside, and is resolved once, at the edge.
 */
public record PaymentMethod(UUID id, PaymentMethodKind kind) {

	/** BR-4: only this kind defers to a statement. */
	public boolean defersToAStatement() {
		return kind == PaymentMethodKind.CREDIT_CARD;
	}
}
