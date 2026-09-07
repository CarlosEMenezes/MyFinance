package ie.budgetTracker.application.dashboard.dto;

import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole overview, in one call (spec §4).
 *
 * One request rather than six, because Overview, Earnings and Expenses are
 * three readings of the same period: served separately they could disagree
 * about what August contained, and the user would have no way to tell which
 * screen was right.
 */
public record DashboardResponse(
		PeriodWindowResponse period,
		PositionResponse position,
		List<PlanRowResponse> earnings,
		List<PlanRowResponse> expenses,
		Totals totals,
		List<UpcomingPaymentResponse> upcoming,
		List<CategorySpendResponse> categorySpend,
		List<AccountResponse> accounts) {

	/**
	 * BR-15: the totals cover the whole period, always.
	 *
	 * A screen may filter what it shows, and it then has to say so. These
	 * figures never change with a filter, which is what stops "total spent"
	 * meaning two different things on two pages.
	 */
	public record Totals(
			long earningsPlanned,
			long earningsReal,
			long expensesPlanned,
			long expensesReal,
			long netPlanned,
			long netReal) {
	}

	/** BR-1 and BR-2, every figure computed server-side. */
	public record PositionResponse(
			/** `availableNow - owed`. May be negative, and BR-1 says to show it in red. */
			long totalMoneyNow,
			long availableNow,
			long owed,
			long owedOnCards,
			long owedOnInstalments,
			long owedOnLoans,
			/** Included in `availableNow`; the net effect on the total is the interest. */
			long borrowed) {
	}

	/** BR-12: something falling due, sorted with the nearest first. */
	public record UpcomingPaymentResponse(
			String key,
			String label,
			String detail,
			LocalDate date,
			/** Negative for money leaving, positive for money arriving. */
			long amount) {
	}

	/**
	 * One bar on the spending breakdown.
	 *
	 * The percentages are computed here so the bar needs no arithmetic: a screen
	 * that scaled them itself would be a second place for the same figure to be
	 * got wrong.
	 */
	public record CategorySpendResponse(
			String categoryId,
			String label,
			long real,
			long planned,
			int percentOfLargest,
			int plannedPercentOfLargest) {
	}
}
