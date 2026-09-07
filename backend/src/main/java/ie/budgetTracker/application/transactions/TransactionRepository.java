package ie.budgetTracker.application.transactions;

import ie.budgetTracker.domain.transactions.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The port through which logged entries are written and read back. */
public interface TransactionRepository {

	/**
	 * Writes an entry against a category and a payment method the user owns.
	 *
	 * Ownership is checked here as well as in the service, because this is the
	 * layer that actually touches the rows: anything belonging to somebody else
	 * is not found rather than forbidden (ADR-11).
	 */
	Transaction create(UUID userId, Transaction transaction);

	/**
	 * Everything that affects the window, dated by when it affects it (BR-4).
	 *
	 * A credit-card purchase belongs to the period its bill falls in, not the
	 * one it was made in. Reading it by the day it was spent would put an
	 * August purchase into August while its plan sat in September, and the
	 * variance between them would be nonsense in both months.
	 */
	List<Transaction> findForUserInPeriod(UUID userId, LocalDate from, LocalDate to);
}
