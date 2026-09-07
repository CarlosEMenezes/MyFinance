package ie.budgetTracker.domain.cards;

import java.time.LocalDate;

/**
 * The three dates a credit-card screen shows (BR-4).
 *
 * They answer two different questions, and a card screen needs both:
 *
 *   - {@code nextBillDate} is "what leaves my account next".
 *   - The other two are "where does spending land", said with the card's own
 *     dates rather than in the abstract, because BR-4 is the rule people most
 *     often get wrong: a purchase does not cost money on the day it is spent,
 *     and one day later can cost a whole extra month of credit.
 *
 * BR-5: a debit card has none of these, and never holds one of these records.
 */
public record CardCycleDates(
		LocalDate nextBillDate,
		LocalDate billDateOnClosingDay,
		LocalDate billDateAfterClosingDay) {
}
