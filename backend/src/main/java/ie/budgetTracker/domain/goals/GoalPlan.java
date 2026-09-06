package ie.budgetTracker.domain.goals;

import java.math.BigDecimal;

/** What reaching a goal by its target date takes (BR-11). */
public record GoalPlan(
		/** What is left to save. Zero once reached, never negative. */
		BigDecimal gap,
		/** Per contribution, at the chosen frequency. */
		BigDecimal contributionPerPeriod,
		/**
		 * The same requirement stated per month, whatever the frequency. Always
		 * present, so two goals saving on different rhythms stay comparable.
		 */
		BigDecimal monthlyRequirement) {
}
