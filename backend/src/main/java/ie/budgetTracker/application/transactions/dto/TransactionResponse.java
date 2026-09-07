package ie.budgetTracker.application.transactions.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A logged entry, shaped as frontend/src/types/api.ts froze it.
 *
 * All three BR-8 figures travel together: what was typed, what every total is
 * stated in, and the rate between them. Opening the entry later shows it as it
 * was entered, and no screen has to re-derive anything to do that.
 *
 * `fxRate` is a `BigDecimal` and reaches the wire as a JSON number. A `double`
 * would carry its binary representation into a value that is stored forever
 * (spec §0.5, CLAUDE.md gotcha 30).
 */
public record TransactionResponse(
		UUID id,
		TransactionType type,
		UUID categoryId,
		long amount,
		Currency currency,
		long amountInDefaultCurrency,
		BigDecimal fxRate,
		LocalDate date,
		/** An account id or a card id, whichever was chosen. */
		UUID paymentMethodId,
		String note,
		UUID instalmentPlanId,
		UUID loanId,
		/** BR-4: the bill date a card expense lands on. Null when settled at once. */
		LocalDate plannedExpenseDate) {

	public static TransactionResponse from(Transaction transaction) {
		return new TransactionResponse(
				transaction.id(),
				transaction.type(),
				transaction.categoryId(),
				Money.toMinorUnits(transaction.amount()),
				transaction.currency(),
				Money.toMinorUnits(transaction.amountInDefaultCurrency()),
				transaction.fxRate(),
				transaction.date(),
				transaction.paymentMethod().id(),
				transaction.note(),
				transaction.instalmentPlanId(),
				transaction.loanId(),
				transaction.plannedExpenseDate());
	}
}
