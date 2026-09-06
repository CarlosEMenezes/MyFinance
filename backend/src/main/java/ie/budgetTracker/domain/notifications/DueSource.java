package ie.budgetTracker.domain.notifications;

/**
 * Where a due payment came from (BR-12).
 *
 * The queue is derived from these five and nothing else. Only read state is
 * persisted; the payments themselves are recomputed every time, so there is no
 * stored notification to go stale when a card is paid or a loan is settled.
 */
public enum DueSource {
	CARD_BILL,
	LOAN,
	INSTALMENT,
	DIRECT_DEBIT,
	SUBSCRIPTION
}
