package ie.budgetTracker.domain.position;

import java.math.BigDecimal;

/**
 * One account's contribution to the position (BR-13).
 *
 * There is no pocket here on purpose. A pocket's balance is already inside its
 * parent account's, so a position that knew about pockets would be one edit
 * away from counting them twice. The only safe design is for the position to
 * be unable to see them at all.
 */
public record AccountBalance(BigDecimal balance, boolean includeInTotals) {
}
