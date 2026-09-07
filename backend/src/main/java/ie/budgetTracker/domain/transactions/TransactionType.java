package ie.budgetTracker.domain.transactions;

/**
 * What a logged entry did with money.
 *
 * SAVING is its own kind rather than an expense with a label: money moved into
 * a pocket has left the spending plan without leaving the person, and BR-1
 * counts it differently from money that is gone.
 */
public enum TransactionType {
	EXPENSE,
	EARNING,
	SAVING
}
