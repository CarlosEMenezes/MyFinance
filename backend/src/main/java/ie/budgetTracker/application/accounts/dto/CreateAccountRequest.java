package ie.budgetTracker.application.accounts.dto;

import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.money.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `POST /accounts`. */
public record CreateAccountRequest(
		@NotBlank @Size(max = 200) String name,
		@NotNull AccountKind kind,
		/** Minor units, and zero is a real opening balance. */
		long balance,
		@NotNull Currency currency,
		boolean includeInTotals,
		@Size(max = 500) String note) {
}
