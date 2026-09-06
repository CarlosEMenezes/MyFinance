package ie.budgetTracker.domain.goals;

import java.math.BigDecimal;

/**
 * Whether a goal fits in what the plan leaves spare (BR-11).
 *
 * `surplus` is signed on purpose: positive is what would be left over,
 * negative is the shortfall. One figure, so a screen states the surplus or the
 * shortfall from the same number rather than choosing between two fields.
 */
public record Feasibility(boolean achievable, BigDecimal surplus) {
}
