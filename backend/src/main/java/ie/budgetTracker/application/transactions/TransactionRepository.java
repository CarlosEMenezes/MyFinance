package ie.budgetTracker.application.transactions;

import ie.budgetTracker.domain.transactions.Transaction;
import java.util.UUID;

/**
 * The port through which logged entries are written.
 *
 * Only a write for now. Reading transactions is what the dashboard does, and
 * the dashboard is spec §6 step 8 - adding a finder before anything asks a
 * question of it would be scaffolding, and spec §0.4 says not to.
 */
public interface TransactionRepository {

	/**
	 * Writes an entry against a category and a payment method the user owns.
	 *
	 * Ownership is checked here as well as in the service, because this is the
	 * layer that actually touches the rows: anything belonging to somebody else
	 * is not found rather than forbidden (ADR-11).
	 */
	Transaction create(UUID userId, Transaction transaction);
}
