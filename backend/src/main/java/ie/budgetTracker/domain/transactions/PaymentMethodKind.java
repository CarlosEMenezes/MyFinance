package ie.budgetTracker.domain.transactions;

/**
 * The three things money can leave through, kept apart because BR-4 and BR-5
 * treat them differently.
 *
 * A debit card is not a lighter credit card here: it is an account with a
 * different piece of plastic in front of it, and its spending lands the same
 * day.
 */
public enum PaymentMethodKind {
	ACCOUNT,
	CREDIT_CARD,
	DEBIT_CARD
}
