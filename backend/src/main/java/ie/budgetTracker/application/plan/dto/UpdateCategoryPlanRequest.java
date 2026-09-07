package ie.budgetTracker.application.plan.dto;

import ie.budgetTracker.domain.plan.Frequency;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * `PATCH /categories/{id}` — BR-14 inline plan editing.
 *
 * Every field is optional because the plan is edited one cell at a time: a
 * request that resubmitted the whole category on each keystroke would
 * overwrite a change made in another tab with a stale copy of it.
 *
 * A record is safe here, unlike `UpdateUserRequest`, because none of these
 * fields is clearable. A missing key means "not mentioned" and a null means
 * the same thing, so the two never have to be told apart (CLAUDE.md gotcha
 * 32).
 */
public record UpdateCategoryPlanRequest(
		/** Minor units, per occurrence. */
		@PositiveOrZero Long plannedAmount,
		Frequency plannedFrequency,
		LocalDate anchorDate,
		@Size(max = 200) String group) {

	/** An empty PATCH would be answered 200 for having saved nothing. */
	public boolean changesNothing() {
		return plannedAmount == null && plannedFrequency == null && anchorDate == null
				&& group == null;
	}
}
