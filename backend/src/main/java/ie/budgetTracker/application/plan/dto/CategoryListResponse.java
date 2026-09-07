package ie.budgetTracker.application.plan.dto;

import java.util.List;

/**
 * `GET /categories?period=…`.
 *
 * The window comes with the list because BR-10 counts occurrences against real
 * dates, and where a period starts and ends is the server's to decide. The
 * client counts *within* that window while somebody edits a frequency or an
 * anchor, which is the optimistic case ADR-7 allows.
 */
public record CategoryListResponse(
		PeriodWindowResponse period,
		List<CategoryResponse> categories) {
}
