package ie.budgetTracker.domain.plan;

import java.math.BigDecimal;

/** A category's difference from its plan, and what that difference means. */
public record Variance(BigDecimal amount, VarianceTone tone) {
}
