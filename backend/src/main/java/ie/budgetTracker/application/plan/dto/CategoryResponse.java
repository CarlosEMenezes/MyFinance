package ie.budgetTracker.application.plan.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A category and its plan, shaped as frontend/src/types/api.ts froze it.
 *
 * `plannedAmount` is per occurrence, in `plannedFrequency`. What it comes to
 * over the window is deliberately absent: BR-10 makes that a function of the
 * window, and the window is on the list this row belongs to. A field here
 * would be the same figure stated twice, in two places that could disagree.
 */
public record CategoryResponse(
		UUID id,
		CategoryType type,
		String name,
		String group,
		long plannedAmount,
		Frequency plannedFrequency,
		/** BR-10: the date the recurrence is counted from. */
		LocalDate anchorDate,
		boolean archived) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(
				category.id(),
				category.type(),
				category.name(),
				category.group(),
				Money.toMinorUnits(category.plannedAmount()),
				category.plannedFrequency(),
				category.anchorDate(),
				category.archived());
	}
}
