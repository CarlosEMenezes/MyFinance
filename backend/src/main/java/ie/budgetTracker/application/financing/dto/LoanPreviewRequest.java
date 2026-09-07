package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.domain.plan.Frequency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** `POST /loans/preview` — BR-7 and BR-2 for money not yet borrowed. */
public record LoanPreviewRequest(
		@NotNull @Positive Long principal,
		@NotNull @Positive Integer instalmentCount,
		@NotNull @Positive Long instalmentAmount,
		@NotNull Frequency frequency) {
}
