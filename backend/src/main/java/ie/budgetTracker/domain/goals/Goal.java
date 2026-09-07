package ie.budgetTracker.domain.goals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Something being saved for, by a date (BR-11).
 *
 * Only what was decided is here. The gap, the required contribution and the
 * pace marker all move with today, so storing any of them would be keeping a
 * figure that was true on the morning it was written.
 *
 * {@code savedAmount} has exactly one source of truth, and this is it. BR-18
 * is explicit that tagging a goal never allocates toward it: a second writer
 * would change BR-11's arithmetic without changing BR-11.
 */
public record Goal(
		UUID id,
		String name,
		BigDecimal targetAmount,
		LocalDate targetDate,
		BigDecimal savedAmount,
		ContributionFrequency contributionFrequency,
		/** Optional. Binding names a place; it does not move money (BR-13). */
		UUID pocketId,
		int rank,
		/** BR-11's pace marker measures from here. */
		LocalDate startedOn) {
}
