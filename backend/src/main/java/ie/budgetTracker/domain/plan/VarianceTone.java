package ie.budgetTracker.domain.plan;

/**
 * How a variance should read (BR-9).
 *
 * A tone, not a colour. The domain says whether a figure is welcome; which
 * green the screen paints is the design system's business, and keeping that
 * decision out of here is what stops a rule and a stylesheet disagreeing.
 */
public enum VarianceTone {
	GOOD,
	BAD,
	/** Exactly on plan. Grey, never green: matching the plan is not a win. */
	NEUTRAL
}
