package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.goals.ContributionFrequency;
import ie.budgetTracker.domain.goals.Goal;
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
 * A goal as the database holds it (BR-11).
 *
 * No derived column, and there must not be one: the gap, the contribution and
 * the pace all move with today, so a stored copy would be a figure that was
 * true on the morning it was written.
 */
@Entity
@Table(name = "goal")
public class GoalEntity {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	private String name;

	@Column(name = "target_amount")
	private BigDecimal targetAmount;

	@Column(name = "target_date")
	private LocalDate targetDate;

	/** BR-11 and BR-18: one source of truth, and this is it. */
	@Column(name = "saved_amount")
	private BigDecimal savedAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "contribution_frequency")
	private ContributionFrequency contributionFrequency;

	/**
	 * A plain id rather than an association.
	 *
	 * Binding a goal to a pocket names a place; it must not become a path for
	 * editing the pocket, and BR-13 keeps a pocket's balance inside its account.
	 */
	@Column(name = "pocket_id")
	private UUID pocketId;

	@Column(name = "priority_rank")
	private int priorityRank;

	@Column(name = "started_on")
	private LocalDate startedOn;

	protected GoalEntity() {
		// JPA.
	}

	GoalEntity(UserEntity user, Goal goal) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.name = goal.name();
		this.targetAmount = goal.targetAmount();
		this.targetDate = goal.targetDate();
		this.savedAmount = goal.savedAmount();
		this.contributionFrequency = goal.contributionFrequency();
		this.pocketId = goal.pocketId();
		this.priorityRank = goal.rank();
		this.startedOn = goal.startedOn();
	}

	Goal toDomain() {
		return new Goal(id, name, targetAmount, targetDate, savedAmount, contributionFrequency,
				pocketId, priorityRank, startedOn);
	}
}
