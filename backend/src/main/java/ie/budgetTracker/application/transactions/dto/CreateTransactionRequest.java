package ie.budgetTracker.application.transactions.dto;

import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.transactions.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `POST /transactions` — what the log form commits.
 *
 * The amount comes in the currency it was logged in, with no conversion done
 * anywhere near the sender. BR-8 makes the rate the server's to look up and to
 * refuse, and a client that sent `amountInDefaultCurrency` would be guessing at
 * exactly the moment the rule says not to.
 *
 * `paymentMethodId` is one id because a person picks one thing from one list.
 * Whether that thing is an account or a card is resolved on this side, and it
 * is what decides BR-4.
 */
public record CreateTransactionRequest(
		@NotNull TransactionType type,
		@NotNull UUID categoryId,
		/** Minor units, in `currency`. */
		@NotNull @PositiveOrZero Long amount,
		@NotNull Currency currency,
		@NotNull LocalDate date,
		@NotNull UUID paymentMethodId,
		@Size(max = 500) String note,
		/** BR-6: present when the purchase is being spread over instalments. */
		@Valid Financing financing) {

	/** The instalment terms, as the log form offers them (BR-6). */
	public record Financing(
			@NotNull @Positive Integer instalmentCount,
			@NotNull @Positive Long instalmentAmount,
			@NotNull Frequency frequency) {
	}
}
