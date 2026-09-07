package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.domain.plan.Frequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `POST /loans` — principal, terms and deposit account, in one write.
 *
 * The deposit account is required, because BR-2 raises what is available by
 * the principal and it has to be raised somewhere in particular. A loan with
 * nowhere to land would move the position without moving any balance.
 */
public record CreateLoanRequest(
		@NotBlank @Size(max = 200) String label,
		/** Minor units. */
		@NotNull @Positive Long principal,
		@NotNull @Positive Integer instalmentCount,
		@NotNull @Positive Long instalmentAmount,
		@NotNull Frequency frequency,
		@NotNull LocalDate firstDueDate,
		@NotNull UUID depositAccountId,
		/** Usually zero; a loan already part-repaid can be recorded as it stands. */
		@PositiveOrZero Integer instalmentsPaid) {
}
