package ie.budgetTracker.application.financing.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanAnalysis;
import ie.budgetTracker.domain.plan.Frequency;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `GET /loans`, shaped as frontend/src/types/api.ts froze it.
 *
 * `earlyPayoffSaving` is on the payload because BR-7 requires it to be shown,
 * and a screen that subtracted two of the other fields for itself would be one
 * refactor away from subtracting the wrong two.
 */
public record LoanResponse(
		UUID id,
		String label,
		long principal,
		int instalmentCount,
		long instalmentAmount,
		Frequency frequency,
		int instalmentsPaid,
		LocalDate firstDueDate,
		UUID depositAccountId,
		InterestSummaryResponse interest,
		int instalmentsRemaining,
		/** The remaining instalments at face value. */
		long remainingRepayable,
		/** BR-7: those instalments discounted back to today. */
		long settlementFigureToday,
		/** BR-7: what settling now saves. Required to be shown. */
		long earlyPayoffSaving) {

	public static LoanResponse from(Loan loan, LoanAnalysis analysis) {
		return new LoanResponse(
				loan.id(),
				loan.label(),
				Money.toMinorUnits(loan.terms().principal()),
				loan.terms().instalmentCount(),
				Money.toMinorUnits(loan.terms().instalmentAmount()),
				loan.terms().frequency(),
				loan.terms().instalmentsPaid(),
				loan.firstDueDate(),
				loan.depositAccountId(),
				InterestSummaryResponse.from(analysis.interest()),
				analysis.instalmentsRemaining(),
				Money.toMinorUnits(analysis.remainingRepayable()),
				Money.toMinorUnits(analysis.settlementFigureToday()),
				Money.toMinorUnits(analysis.earlyPayoffSaving()));
	}
}
