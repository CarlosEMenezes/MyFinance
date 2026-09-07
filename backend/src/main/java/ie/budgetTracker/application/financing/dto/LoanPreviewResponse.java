package ie.budgetTracker.application.financing.dto;

/**
 * What taking this loan would cost and what it would do to the position.
 *
 * BR-2 is the reason both sides are here: borrowing raises what is available
 * by the principal *and* what is owed by the whole repayment, so the net
 * effect on the total is exactly the interest. Showing only one side would
 * make a loan look like income.
 */
public record LoanPreviewResponse(
		InterestSummaryResponse interest,
		long addsToAvailable,
		long addsToOwed,
		long settlementFigureToday) {
}
