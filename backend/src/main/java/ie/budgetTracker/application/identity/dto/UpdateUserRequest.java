package ie.budgetTracker.application.identity.dto;

import ie.budgetTracker.application.identity.UserChanges;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Optional;

/**
 * `PATCH /users/me`. Every field is optional: Settings saves one at a time.
 *
 * A class with setters rather than a record, and that is not a style choice.
 * Age, role and country need "not mentioned" and "clear it" to stay
 * distinguishable, and a record cannot express the difference: Jackson binds
 * an ABSENT {@code Optional} component to {@code Optional.empty()}, which is
 * indistinguishable from an explicit null. Saving a name would then wipe the
 * age beside it.
 *
 * A setter is only called when the key is present in the body, so a field left
 * null here really was left out. That is the whole reason for the shape.
 */
public class UpdateUserRequest {

	@Size(max = 200)
	private String name;

	private Optional<@Min(0) @Max(150) Integer> age;

	private Optional<@Size(max = 200) String> role;

	private Optional<@Size(max = 200) String> country;

	private PayCycle payCycle;

	private Currency defaultCurrency;

	private String dateFormat;

	private WeekStart weekStart;

	private PreferencesRequest preferences;

	public record PreferencesRequest(
			boolean autoConvertForeignAmounts,
			boolean roundGoalContributionsUp,
			boolean carryUnspentBudget) {
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAge(Integer age) {
		this.age = Optional.ofNullable(age);
	}

	public void setRole(String role) {
		this.role = Optional.ofNullable(blankToNull(role));
	}

	public void setCountry(String country) {
		this.country = Optional.ofNullable(blankToNull(country));
	}

	public void setPayCycle(PayCycle payCycle) {
		this.payCycle = payCycle;
	}

	public void setDefaultCurrency(Currency defaultCurrency) {
		this.defaultCurrency = defaultCurrency;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public void setWeekStart(WeekStart weekStart) {
		this.weekStart = weekStart;
	}

	public void setPreferences(PreferencesRequest preferences) {
		this.preferences = preferences;
	}

	public Optional<Integer> getAge() {
		return age;
	}

	public UserChanges toChanges() {
		return new UserChanges(
				name,
				age,
				role,
				country,
				payCycle,
				defaultCurrency,
				dateFormat == null ? null : DateFormatPreference.valueOf(dateFormat.replace('-', '_')),
				weekStart,
				preferences == null ? null : new UserPreferences(
						preferences.autoConvertForeignAmounts(),
						preferences.roundGoalContributionsUp(),
						preferences.carryUnspentBudget()));
	}

	/** An emptied field arrives as "", and an empty role is not a role. */
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
