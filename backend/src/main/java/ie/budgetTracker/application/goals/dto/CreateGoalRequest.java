package ie.budgetTracker.application.goals.dto;

import ie.budgetTracker.domain.goals.ContributionFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `POST /goals` — BR-11.
 *
 * The target date is required and must be in the future: BR-11 divides the gap
 * by the periods left, and a date in the past leaves none to divide by.
 *
 * `savedAmount` may be given, because a goal often starts with something
 * already put by. Nothing else ever writes to it afterwards - BR-11 has one
 * source of truth for what is saved.
 */
public record CreateGoalRequest(
		@NotBlank @Size(max = 200) String name,
		/** Minor units. */
		@NotNull @Positive Long targetAmount,
		@NotNull LocalDate targetDate,
		@PositiveOrZero Long savedAmount,
		@NotNull ContributionFrequency contributionFrequency,
		/** Optional: binding names a place, it does not move money (BR-13). */
		UUID pocketId,
		/** Goals are ranked, so the list has an order somebody chose. */
		@PositiveOrZero Integer rank) {
}
