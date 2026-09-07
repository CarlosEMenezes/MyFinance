package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InterestSummary;
import ie.budgetTracker.domain.plan.Frequency;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `GET /instalment-plans`, shaped as frontend/src/types/api.ts froze it.
 *
 * The interest arrives worked out. BR-6 solves the periodic rate by bisection
 * and a screen has no business running a solver, so this is the figure and not
 * the ingredients for one (ADR-7).
 */
public record InstalmentPlanResponse(
		UUID id,
		UUID cardId,
		String label,
		long cashPrice,
		int instalmentCount,
		long instalmentAmount,
		Frequency frequency,
		int instalmentsPaid,
		/** BR-4: the bill the first instalment lands on, not the purchase date. */
		LocalDate firstDueDate,
		InterestSummaryResponse interest) {

	public static InstalmentPlanResponse from(InstalmentPlan plan, InterestSummary interest) {
		return new InstalmentPlanResponse(
				plan.id(),
				plan.cardId(),
				plan.label(),
				Money.toMinorUnits(plan.terms().cashPrice()),
				plan.terms().instalmentCount(),
				Money.toMinorUnits(plan.terms().instalmentAmount()),
				plan.terms().frequency(),
				plan.instalmentsPaid(),
				plan.firstDueDate(),
				InterestSummaryResponse.from(interest));
	}
}
