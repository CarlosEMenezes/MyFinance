package ie.budgetTracker.application.financing;

import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.Loan;
import java.util.List;
import java.util.UUID;

/**
 * The port through which instalment plans and loans are read and written.
 *
 * One port for both, because they are the same shape of thing seen from two
 * sides - an annuity with terms, an owner and a schedule - and every consumer
 * that wants one wants the other beside it (BR-1, BR-3).
 *
 * Neither is stored with an interest figure. BR-6 and BR-7 solve from the
 * terms, and the answers move as instalments are paid.
 */
public interface FinancingRepository {

	List<InstalmentPlan> findPlansForUser(UUID userId);

	List<Loan> findLoansForUser(UUID userId);

	/**
	 * Writes a plan against a card the user owns.
	 *
	 * A card belonging to somebody else is not found rather than forbidden
	 * (ADR-11).
	 */
	InstalmentPlan createPlan(UUID userId, InstalmentPlan plan);

	/** Writes a loan against an account the user owns. */
	Loan createLoan(UUID userId, Loan loan);
}
