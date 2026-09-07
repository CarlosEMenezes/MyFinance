package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
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
 * A plan as the database holds it (BR-6).
 *
 * There is no interest column, and there must not be one: BR-6 solves the rate
 * from the terms, and a stored APR would be a figure that was true once,
 * sitting beside terms that have since moved.
 */
@Entity
@Table(name = "instalment_plan")
public class InstalmentPlanEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "card_id", nullable = false)
	private CardEntity card;

	private String label;

	@Column(name = "cash_price")
	private BigDecimal cashPrice;

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

	protected InstalmentPlanEntity() {
		// JPA.
	}

	InstalmentPlanEntity(CardEntity card, InstalmentPlan plan) {
		this.id = UUID.randomUUID();
		this.card = card;
		this.label = plan.label();
		this.cashPrice = plan.terms().cashPrice();
		this.instalmentCount = plan.terms().instalmentCount();
		this.instalmentAmount = plan.terms().instalmentAmount();
		this.frequency = plan.terms().frequency();
		this.instalmentsPaid = plan.instalmentsPaid();
		this.firstDueDate = plan.firstDueDate();
	}

	UUID getId() {
		return id;
	}

	InstalmentPlan toDomain() {
		return new InstalmentPlan(id, card.getId(), label,
				new InstalmentTerms(cashPrice, instalmentCount, instalmentAmount, frequency),
				instalmentsPaid, firstDueDate);
	}
}
