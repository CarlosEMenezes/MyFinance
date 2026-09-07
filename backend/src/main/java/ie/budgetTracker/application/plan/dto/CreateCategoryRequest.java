package ie.budgetTracker.application.plan.dto;

import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * `POST /categories` — BR-14.
 *
 * Nothing about the plan is optional. Creating a category creates its planned
 * amount and frequency, so a request without them is refused rather than
 * defaulted: a default would be a plan the user never chose, and every
 * variance drawn against it afterwards would be measured from a number nobody
 * meant.
 */
public record CreateCategoryRequest(
		@NotBlank @Size(max = 200) String name,
		@NotNull CategoryType type,
		@NotBlank @Size(max = 200) String group,
		/** Minor units, per occurrence. Zero is a real plan; negative is not. */
		@NotNull @PositiveOrZero Long plannedAmount,
		@NotNull Frequency plannedFrequency,
		/** BR-10: what the occurrences are counted from. */
		@NotNull LocalDate anchorDate) {
}
