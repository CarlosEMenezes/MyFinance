package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
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

/** A category and its plan as the database holds it (BR-14). */
@Entity
@Table(name = "category")
public class CategoryEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Enumerated(EnumType.STRING)
	private CategoryType type;

	private String name;

	/** "group" is reserved in SQL, so the column is prefixed and mapped here. */
	@Column(name = "category_group")
	private String group;

	@Column(name = "planned_amount")
	private BigDecimal plannedAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "planned_frequency")
	private Frequency plannedFrequency;

	@Column(name = "anchor_date")
	private LocalDate anchorDate;

	private boolean archived;

	protected CategoryEntity() {
		// JPA.
	}

	CategoryEntity(UserEntity user, Category category) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.type = category.type();
		apply(category);
	}

	/**
	 * Everything an edit may touch, in one place.
	 *
	 * `type` is not here on purpose: an expense category that became an earning
	 * one would take its whole history to the other side of BR-9, silently
	 * reversing every variance already recorded against it.
	 */
	void apply(Category category) {
		this.name = category.name();
		this.group = category.group();
		this.plannedAmount = category.plannedAmount();
		this.plannedFrequency = category.plannedFrequency();
		this.anchorDate = category.anchorDate();
		this.archived = category.archived();
	}

	UUID getId() {
		return id;
	}

	Category toDomain() {
		return new Category(id, type, name, group, plannedAmount, plannedFrequency, anchorDate,
				archived);
	}
}
