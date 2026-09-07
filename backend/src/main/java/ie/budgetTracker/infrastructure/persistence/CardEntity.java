package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CardKind;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
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
import java.util.UUID;

/**
 * A card as the database holds it (BR-4, BR-5).
 *
 * The table has to be one shape for two kinds, so the credit columns are
 * nullable here in a way the domain's sealed {@code Card} never is. That
 * looseness stops at {@link #toDomain()}: a row comes back as a
 * {@link CreditCard} or a {@link DebitCard}, and nothing above this class ever
 * meets a card whose cycle might or might not be there.
 *
 * The account is not optional, and there is no user column: a card belongs to
 * whoever owns the account it settles from, so ownership has one source of
 * truth rather than two that could disagree.
 */
@Entity
@Table(name = "card")
public class CardEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "account_id", nullable = false)
	private AccountEntity account;

	private String name;

	@Enumerated(EnumType.STRING)
	private CardKind kind;

	@Column(name = "credit_limit")
	private BigDecimal creditLimit;

	@Column(name = "current_balance")
	private BigDecimal currentBalance;

	@Column(name = "closing_day")
	private Integer closingDay;

	@Column(name = "due_day")
	private Integer dueDay;

	protected CardEntity() {
		// JPA.
	}

	CardEntity(AccountEntity account, Card card) {
		this.id = UUID.randomUUID();
		this.account = account;
		this.name = card.name();
		this.kind = card.kind();

		if (card instanceof CreditCard credit) {
			this.creditLimit = credit.creditLimit();
			this.currentBalance = credit.currentBalance();
			this.closingDay = credit.cycle().closingDay();
			this.dueDay = credit.cycle().dueDay();
		}
		// A debit card leaves all four null, which is what the check constraint
		// on this table insists on (BR-5).
	}

	CardKind getKind() {
		return kind;
	}

	UUID getId() {
		return id;
	}

	Card toDomain() {
		if (kind == CardKind.DEBIT) {
			return new DebitCard(id, name, account.getId());
		}
		return new CreditCard(id, name, account.getId(), creditLimit, currentBalance,
				new StatementCycle(closingDay, dueDay));
	}
}
