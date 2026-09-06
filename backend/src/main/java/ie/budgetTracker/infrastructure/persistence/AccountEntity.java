package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.money.Currency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An account as the database holds it (BR-13). */
@Entity
@Table(name = "account")
public class AccountEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	private String name;

	@Enumerated(EnumType.STRING)
	private AccountKind kind;

	/** The whole account, pockets included. Never the sum of anything. */
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	private Currency currency;

	@Column(name = "include_in_totals")
	private boolean includeInTotals;

	private String note;

	// Cascade and orphan removal because a pocket cannot outlive its account:
	// BR-13 makes it part of one, not a thing beside it.
	@OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.EAGER)
	@OrderBy("name")
	private List<PocketEntity> pockets = new ArrayList<>();

	protected AccountEntity() {
		// JPA.
	}

	AccountEntity(UserEntity user, Account account) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.name = account.name();
		this.kind = account.kind();
		this.balance = account.balance();
		this.currency = account.currency();
		this.includeInTotals = account.includeInTotals();
		this.note = account.note();
	}

	/**
	 * BR-13: adding a pocket does not move the account's balance.
	 *
	 * There is deliberately no code here that touches `balance`. The pocket
	 * names part of money that is already counted, and an implementation that
	 * added to the parent would count the same euro twice.
	 */
	PocketEntity addPocket(String name, BigDecimal balance) {
		PocketEntity pocket = new PocketEntity(this, name, balance);
		pockets.add(pocket);
		return pocket;
	}

	Account toDomain() {
		return new Account(id, name, kind, balance, currency, includeInTotals, note,
				pockets.stream().map(PocketEntity::toDomain).toList());
	}

	UUID getId() {
		return id;
	}
}
