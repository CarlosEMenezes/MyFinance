package ie.budgetTracker.application.identity;

import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import java.util.Optional;

/**
 * A partial edit to the profile.
 *
 * Settings saves one field at a time, so most of these are null for "not
 * mentioned" and are simply left alone.
 *
 * Age, role and country are different: clearing them is a real thing to want,
 * and a bare null could not tell "leave it" from "remove it". They are
 * {@link Optional}, where a null field means untouched and an empty Optional
 * means cleared. Using null for both would silently wipe a profile every time
 * one other field was saved.
 */
public record UserChanges(
		String name,
		Optional<Integer> age,
		Optional<String> role,
		Optional<String> country,
		PayCycle payCycle,
		Currency defaultCurrency,
		DateFormatPreference dateFormat,
		WeekStart weekStart,
		UserPreferences preferences) {

	User applyTo(User user) {
		return new User(
				user.id(),
				name == null ? user.name() : name,
				age == null ? user.age() : age.orElse(null),
				role == null ? user.role() : role.orElse(null),
				country == null ? user.country() : country.orElse(null),
				payCycle == null ? user.payCycle() : payCycle,
				defaultCurrency == null ? user.defaultCurrency() : defaultCurrency,
				dateFormat == null ? user.dateFormat() : dateFormat,
				weekStart == null ? user.weekStart() : weekStart,
				preferences == null ? user.preferences() : preferences);
	}
}
