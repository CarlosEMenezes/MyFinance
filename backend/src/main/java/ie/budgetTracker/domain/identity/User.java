package ie.budgetTracker.domain.identity;

import ie.budgetTracker.domain.money.Currency;
import java.util.UUID;

/**
 * Who the figures belong to.
 *
 * Age, role and country are nullable because none of them changes a figure and
 * spec §0.7 asks for the minimum. A profile with only a name is a usable one.
 */
public record User(
		UUID id,
		String name,
		Integer age,
		String role,
		String country,
		PayCycle payCycle,
		/** BR-8: every total is stated in this. */
		Currency defaultCurrency,
		DateFormatPreference dateFormat,
		WeekStart weekStart,
		UserPreferences preferences) {
}
