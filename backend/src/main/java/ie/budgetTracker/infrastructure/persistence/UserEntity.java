package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The user as the database holds it.
 *
 * A separate type from the domain's User on purpose: this one has a no-arg
 * constructor, mutable fields and JPA annotations, none of which belong in a
 * record that models a rule. The mapping between them is the only place those
 * two shapes meet.
 */
@Entity
@Table(name = "app_user")
public class UserEntity {

	@Id
	private UUID id;

	/** Lower-cased before it gets here, so the unique index means what it says. */
	private String email;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "created_at")
	private java.time.Instant createdAt;

	private String name;

	private Integer age;

	private String role;

	private String country;

	@Enumerated(EnumType.STRING)
	@Column(name = "pay_cycle")
	private PayCycle payCycle;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_currency")
	private Currency defaultCurrency;

	@Enumerated(EnumType.STRING)
	@Column(name = "date_format")
	private DateFormatPreference dateFormat;

	@Enumerated(EnumType.STRING)
	@Column(name = "week_start")
	private WeekStart weekStart;

	@Column(name = "auto_convert_foreign_amounts")
	private boolean autoConvertForeignAmounts;

	@Column(name = "round_goal_contributions_up")
	private boolean roundGoalContributionsUp;

	@Column(name = "carry_unspent_budget")
	private boolean carryUnspentBudget;

	protected UserEntity() {
		// JPA.
	}

	static UserEntity from(User user) {
		UserEntity entity = new UserEntity();
		entity.id = user.id();
		entity.apply(user);
		return entity;
	}

	static UserEntity registering(String email, String passwordHash, User profile,
			java.time.Instant now) {
		UserEntity entity = new UserEntity();
		entity.id = UUID.randomUUID();
		entity.email = email;
		entity.passwordHash = passwordHash;
		entity.createdAt = now;
		entity.apply(profile);
		return entity;
	}

	String getEmail() {
		return email;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	void apply(User user) {
		this.name = user.name();
		this.age = user.age();
		this.role = user.role();
		this.country = user.country();
		this.payCycle = user.payCycle();
		this.defaultCurrency = user.defaultCurrency();
		this.dateFormat = user.dateFormat();
		this.weekStart = user.weekStart();
		this.autoConvertForeignAmounts = user.preferences().autoConvertForeignAmounts();
		this.roundGoalContributionsUp = user.preferences().roundGoalContributionsUp();
		this.carryUnspentBudget = user.preferences().carryUnspentBudget();
	}

	User toDomain() {
		return new User(id, name, age, role, country, payCycle, defaultCurrency, dateFormat,
				weekStart, new UserPreferences(autoConvertForeignAmounts, roundGoalContributionsUp,
						carryUnspentBudget));
	}

	UUID getId() {
		return id;
	}
}
