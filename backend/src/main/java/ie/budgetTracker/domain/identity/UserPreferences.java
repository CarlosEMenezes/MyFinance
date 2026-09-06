package ie.budgetTracker.domain.identity;

/** Choices that change how figures are presented, never what they are. */
public record UserPreferences(
		boolean autoConvertForeignAmounts,
		boolean roundGoalContributionsUp,
		boolean carryUnspentBudget) {
}
