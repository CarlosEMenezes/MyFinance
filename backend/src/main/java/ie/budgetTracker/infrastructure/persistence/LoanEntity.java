package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.plan.Frequency;
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
 * A loan as the database holds it (BR-7, BR-2).
 *
 * The deposit account is not optional: BR-2 raises what is available by the
 * principal, and it has to be raised somewhere in particular.
 */
@Entity
@Table(name = "loan")
public class LoanEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "deposit_account_id", nullable = false)
	private AccountEntity depositAccount;

	private String label;

	private BigDecimal principal;

	@Column(name = "instalment_count")
	private int instalmentCount;

	@Column(name = "instalment_amount")
	private BigDecimal instalmentAmount;

	@Enumerated(EnumType.STRING)
	private Frequency frequency;

	@Column(name = "instalments_paid")
	private int instalmentsPaid;

	@Column(name = "first_due_date")
	private LocalDate firstDueDate;

	protected LoanEntity() {
		// JPA.
	}

	LoanEntity(AccountEntity depositAccount, Loan loan) {
		this.id = UUID.randomUUID();
		this.depositAccount = depositAccount;
		this.label = loan.label();
		this.principal = loan.terms().principal();
		this.instalmentCount = loan.terms().instalmentCount();
		this.instalmentAmount = loan.terms().instalmentAmount();
		this.frequency = loan.terms().frequency();
		this.instalmentsPaid = loan.terms().instalmentsPaid();
		this.firstDueDate = loan.firstDueDate();
	}

	Loan toDomain() {
		return new Loan(id, label,
				new LoanTerms(principal, instalmentCount, instalmentAmount, frequency,
						instalmentsPaid),
				firstDueDate, depositAccount.getId());
	}
}
