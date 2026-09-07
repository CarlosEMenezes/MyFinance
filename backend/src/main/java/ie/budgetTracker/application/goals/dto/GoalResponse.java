package ie.budgetTracker.application.goals.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.goals.ContributionFrequency;
import ie.budgetTracker.domain.goals.Goal;
import ie.budgetTracker.domain.goals.GoalPlan;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal with BR-11 already worked out, shaped as the contract froze it.
 *
 * `pacePercent` is what turns a progress bar into a judgement: 40% saved says
 * nothing on its own, but 40% against a pace of 25% is ahead and against 70%
 * is a goal about to be missed. `onPace` is stated rather than left to a
 * comparison, so two screens cannot draw the same goal differently.
 */
public record GoalResponse(
		UUID id,
		String name,
		long targetAmount,
		LocalDate targetDate,
		long savedAmount,
		ContributionFrequency contributionFrequency,
		UUID pocketId,
		int rank,
		/** BR-11: what is left to save. Zero once reached, never negative. */
		long gap,
		long contributionPerPeriod,
		/** The same requirement per month, so goals on different rhythms compare. */
		long monthlyRequirement,
		int progressPercent,
		/** Where the plan says progress should have reached by now. */
		int pacePercent,
		boolean onPace) {

	public static GoalResponse from(Goal goal, GoalPlan plan, int progressPercent,
			int pacePercent) {

		return new GoalResponse(
				goal.id(),
				goal.name(),
				Money.toMinorUnits(goal.targetAmount()),
				goal.targetDate(),
				Money.toMinorUnits(goal.savedAmount()),
				goal.contributionFrequency(),
				goal.pocketId(),
				goal.rank(),
				Money.toMinorUnits(plan.gap()),
				Money.toMinorUnits(plan.contributionPerPeriod()),
				Money.toMinorUnits(plan.monthlyRequirement()),
				progressPercent,
				pacePercent,
				progressPercent >= pacePercent);
	}
}
