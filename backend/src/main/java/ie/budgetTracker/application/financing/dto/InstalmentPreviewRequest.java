package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.domain.plan.Frequency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `POST /instalment-plans/preview` — BR-6 for money not yet committed.
 *
 * Nothing is written. Spec §5 lets the log form show these figures live from
 * its own pure function, and ADR-7 allows exactly that for an unsaved figure;
 * this endpoint is the same answer from the side that owns the rule, and is
 * what the two are checked against.
 */
public record InstalmentPreviewRequest(
		@NotNull @Positive Long cashPrice,
		@NotNull @Positive Integer instalmentCount,
		@NotNull @Positive Long instalmentAmount,
		@NotNull Frequency frequency,
		@NotNull UUID cardId,
		@NotNull LocalDate purchaseDate) {
}
