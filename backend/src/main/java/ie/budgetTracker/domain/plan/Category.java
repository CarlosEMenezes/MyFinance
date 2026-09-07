package ie.budgetTracker.domain.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Something money is expected to come from or go to, and the plan for it
 * (BR-14).
 *
 * The plan is not a separate thing that a category might or might not have.
 * BR-14 says creating a category creates its planned amount and frequency, so
 * they are components here and there is no way to build one without them - a
 * category with no plan is a row the plan-vs-real tables cannot draw.
 *
 * `plannedAmount` is per occurrence. What it comes to over a window is BR-10's
 * question, answered by {@link PlanNormaliser} against `anchorDate`, and is
 * deliberately not stored: it changes with the window.
 */
public record Category(
		UUID id,
		CategoryType type,
		String name,
		/** A user-editable grouping, such as "Fixed" or "Self-employed". */
		String group,
		BigDecimal plannedAmount,
		Frequency plannedFrequency,
		/** BR-10: the date the recurrence is counted from. */
		LocalDate anchorDate,
		/** Archived categories are kept, because their history still happened. */
		boolean archived) {
}
