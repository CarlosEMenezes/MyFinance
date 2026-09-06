package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.accounts.Pocket;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A pocket, which exists only inside an account (BR-13).
 *
 * The account is not optional and there is no way to persist one without a
 * parent, which is the schema saying the same thing the rule does.
 */
@Entity
@Table(name = "pocket")
public class PocketEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private AccountEntity account;

	private String name;

	private BigDecimal balance;

	protected PocketEntity() {
		// JPA.
	}

	PocketEntity(AccountEntity account, String name, BigDecimal balance) {
		this.id = UUID.randomUUID();
		this.account = account;
		this.name = name;
		this.balance = balance;
	}

	Pocket toDomain() {
		return new Pocket(id, name, balance);
	}
}
