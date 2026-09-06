package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.domain.accounts.Account;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link AccountRepository}. */
@Repository
class JpaAccountRepository implements AccountRepository {

	private final AccountJpaRepository accounts;
	private final UserJpaRepository users;

	JpaAccountRepository(AccountJpaRepository accounts, UserJpaRepository users) {
		this.accounts = accounts;
		this.users = users;
	}

	@Override
	public List<Account> findAllForUser(UUID userId) {
		return accounts.findByUserIdOrderByNameAsc(userId).stream()
				.map(AccountEntity::toDomain)
				.toList();
	}

	@Override
	public Optional<Account> findForUser(UUID userId, UUID accountId) {
		return accounts.findByUserIdAndId(userId, accountId).map(AccountEntity::toDomain);
	}

	@Override
	public Account create(UUID userId, Account account) {
		UserEntity owner = users.findById(userId)
				.orElseThrow(() -> AppException.notFound("No user with id " + userId));

		return accounts.save(new AccountEntity(owner, account)).toDomain();
	}

	@Override
	public Account addPocket(UUID accountId, String name, BigDecimal balance) {
		AccountEntity account = accounts.findById(accountId)
				.orElseThrow(() -> AppException.notFound("No account with id " + accountId));

		account.addPocket(name, balance);
		return accounts.save(account).toDomain();
	}
}
