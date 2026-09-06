package ie.budgetTracker.application.identity.dto;

import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.money.Currency;
import java.util.UUID;

/**
 * `GET /users/me`, shaped exactly as frontend/src/types/api.ts froze it.
 *
 * A DTO rather than the entity or the domain record, per spec §4: no entity is
 * ever exposed directly, and the wire shape is allowed to change on its own
 * schedule without dragging a rule with it.
 */
public record UserResponse(
		UUID id,
		String name,
		Integer age,
		String role,
		String country,
		PayCycle payCycle,
		Currency defaultCurrency,
		String dateFormat,
		String weekStart,
		PreferencesResponse preferences) {

	public record PreferencesResponse(
			boolean autoConvertForeignAmounts,
			boolean roundGoalContributionsUp,
			boolean carryUnspentBudget) {
	}

	public static UserResponse from(User user) {
		return new UserResponse(
				user.id(),
				user.name(),
				user.age(),
				user.role(),
				user.country(),
				user.payCycle(),
				user.defaultCurrency(),
				// The enum cannot hold a hyphen; the contract says DD-MM-YYYY.
				user.dateFormat().name().replace('_', '-'),
				user.weekStart().name(),
				new PreferencesResponse(
						user.preferences().autoConvertForeignAmounts(),
						user.preferences().roundGoalContributionsUp(),
						user.preferences().carryUnspentBudget()));
	}
}
