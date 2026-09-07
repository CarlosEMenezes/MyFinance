package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.transactions.TransactionRepository;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link TransactionRepository}. */
@Repository
class JpaTransactionRepository implements TransactionRepository {

	private final TransactionJpaRepository entries;
	private final UserJpaRepository users;
	private final CategoryJpaRepository categories;
	private final AccountJpaRepository accounts;
	private final CardJpaRepository cards;

	JpaTransactionRepository(TransactionJpaRepository entries, UserJpaRepository users,
			CategoryJpaRepository categories, AccountJpaRepository accounts,
			CardJpaRepository cards) {
		this.entries = entries;
		this.users = users;
		this.categories = categories;
		this.accounts = accounts;
		this.cards = cards;
	}

	@Override
	public Transaction create(UUID userId, Transaction transaction) {
		UserEntity owner = users.findById(userId)
				.orElseThrow(() -> AppException.notFound("No user with id " + userId));

		// Every association is looked up by owner AND id. The service has already
		// checked, and this checks again, because this is the layer that actually
		// writes the row - and an entry hung off somebody else's category would be
		// a leak nothing downstream could undo (ADR-11).
		CategoryEntity category = categories
				.findByUserIdAndId(userId, transaction.categoryId())
				.orElseThrow(() -> AppException.notFound(
						"No category with id " + transaction.categoryId()));

		UUID methodId = transaction.paymentMethod().id();
		boolean paidByCard = transaction.paymentMethod().kind() != PaymentMethodKind.ACCOUNT;

		CardEntity card = paidByCard
				? cards.findByAccountUserIdAndId(userId, methodId)
						.orElseThrow(() -> AppException.notFound("No card with id " + methodId))
				: null;

		AccountEntity account = paidByCard
				? null
				: accounts.findByUserIdAndId(userId, methodId)
						.orElseThrow(() -> AppException.notFound("No account with id " + methodId));

		return entries.save(new TransactionEntity(owner, category, account, card, transaction))
				.toDomain(transaction.paymentMethod().kind());
	}
}
