package ie.budgetTracker.application.dashboard.dto;

import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.VarianceTone;

/**
 * One category row, normalised to the period and with its variance resolved.
 *
 * Spec §4 is explicit that the frontend must not recompute these, and this
 * record is what that promise looks like: `planned` is already BR-10's
 * real-date count, `variance` is already `real - planned`, and `varianceTone`
 * is already BR-9's reading of it. A screen renders; it does not decide.
 */
public record PlanRowResponse(
		String categoryId,
		String category,
		CategoryType type,
		String group,
		/** For the whole period, counted on real dates (BR-10). */
		long planned,
		long real,
		/** `real - planned`, the same sign convention on both sides (BR-9). */
		long variance,
		VarianceTone varianceTone,
		/** Per occurrence: the figure the inline field edits (BR-14). */
		long perOccurrence,
		Frequency frequency,
		int occurrencesInPeriod,
		/** BR-3's averaged commitment, stated beside the real one. */
		long monthlyEquivalent,
		/**
		 * BR-14: a derived row - "Loan repayments", "Card instalments" - is read
		 * only and is rendered as text, never as an input.
		 */
		boolean derived,
		String paidWith,
		String dueNote,
		/** Set when the amount was logged in another currency (BR-8). */
		String foreignAmount) {
}
