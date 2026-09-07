package ie.budgetTracker.application.financing.dto;

import java.time.LocalDate;

/** What spreading this purchase would cost, and when the first bill lands. */
public record InstalmentPreviewResponse(
		InterestSummaryResponse interest,
		/** BR-4: when the first instalment actually lands. */
		LocalDate firstDueDate) {
}
