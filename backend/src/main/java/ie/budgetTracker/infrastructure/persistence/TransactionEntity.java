package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.transactions.PaymentMethod;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A logged entry as the database holds it (BR-8, BR-4).
 *
 * The payment method is two nullable associations with a check constraint
 * behind them, rather than one loose id: what the method *is* decides BR-4,
 * BR-5 and BR-1, and a single untyped column would make that question be
 * re-answered by a join everywhere it is asked.
 */
@Entity
@Table(name = "transaction_entry")
public class TransactionEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private CategoryEntity category;

	@Enumerated(EnumType.STRING)
	private TransactionType type;

	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private Currency currency;

	@Column(name = "amount_in_default_currency")
	private BigDecimal amountInDefaultCurrency;

	/** BR-8, and never a double: this value is stored forever (spec §0.5). */
	@Column(name = "fx_rate")
	private BigDecimal fxRate;

	@Column(name = "entry_date")
	private LocalDate entryDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "account_id")
	private AccountEntity account;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "card_id")
	private CardEntity card;

	private String note;

	@Column(name = "planned_expense_date")
	private LocalDate plannedExpenseDate;

	protected TransactionEntity() {
		// JPA.
	}

	TransactionEntity(UserEntity user, CategoryEntity category, AccountEntity account,
			CardEntity card, Transaction transaction) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.category = category;
		this.account = account;
		this.card = card;
		this.type = transaction.type();
		this.amount = transaction.amount();
		this.currency = transaction.currency();
		this.amountInDefaultCurrency = transaction.amountInDefaultCurrency();
		this.fxRate = transaction.fxRate();
		this.entryDate = transaction.date();
		this.note = transaction.note();
		this.plannedExpenseDate = transaction.plannedExpenseDate();
	}

	Transaction toDomain(PaymentMethodKind methodKind) {
		UUID methodId = card != null ? card.getId() : account.getId();

		return new Transaction(id, type, category.getId(), amount, currency,
				amountInDefaultCurrency, fxRate, entryDate,
				new PaymentMethod(methodId, methodKind), note, null, null, plannedExpenseDate);
	}
}
