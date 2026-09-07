package ie.budgetTracker.domain.transactions;

import ie.budgetTracker.domain.money.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money that actually moved (BR-8, BR-4).
 *
 * Three fields carry BR-8 together and are meaningless apart: the amount in the
 * currency it was logged in, that amount restated in the user's default
 * currency, and the rate used to get from one to the other. Keeping all three
 * is what lets an entry be reopened and shown as it was typed, while every
 * total stays in one currency.
 *
 * `fxRate` is a `BigDecimal` rather than a `double` for the reason spec §0.5
 * gives: a rate serialised from a `double` carries its binary representation
 * across the wire, and this one is stored forever.
 *
 * `plannedExpenseDate` is BR-4's answer, not the purchase date. For a
 * credit-card expense the money is owed on the bill, and that is the date the
 * plan is built from.
 */
public record Transaction(
		UUID id,
		TransactionType type,
		UUID categoryId,
		/** In the currency it was logged in. */
		BigDecimal amount,
		Currency currency,
		BigDecimal amountInDefaultCurrency,
		/** The rate used at log time. Kept for display, never to recompute. */
		BigDecimal fxRate,
		LocalDate date,
		PaymentMethod paymentMethod,
		String note,
		/** BR-6, when the purchase was spread. Null otherwise. */
		UUID instalmentPlanId,
		/** BR-7, when the entry came from a loan. Null otherwise. */
		UUID loanId,
		/** BR-4: the bill date a card expense lands on. Null when settled at once. */
		LocalDate plannedExpenseDate) {
}
