package ie.budgetTracker.application.accounts;

import ie.budgetTracker.domain.accounts.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The port through which accounts and their pockets are read and written. */
public interface AccountRepository {

	List<Account> findAllForUser(UUID userId);

	Optional<Account> findForUser(UUID userId, UUID accountId);

	Account create(UUID userId, Account account);

	/**
	 * BR-13: a pocket is created against its parent and returns the parent.
	 *
	 * The parent's balance is not touched - the pocket names part of money that
	 * is already there. Returning the whole account rather than the pocket makes
	 * that visible at the call site: what changed is the account's composition,
	 * not its total.
	 */
	Account addPocket(UUID accountId, String name, java.math.BigDecimal balance);
}
