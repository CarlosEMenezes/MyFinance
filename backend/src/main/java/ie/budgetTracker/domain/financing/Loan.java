package ie.budgetTracker.domain.financing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Money borrowed, and the account it landed in (BR-7, BR-2).
 *
 * The deposit account is part of what a loan is rather than a note about it:
 * BR-2 says borrowing raises what is available by the principal, and it has to
 * be raised somewhere in particular.
 *
 * As with an instalment plan, the terms are stored and the interest is not.
 * BR-7's settlement figure changes with every instalment paid, so it is solved
 * on demand and never written down.
 */
public record Loan(
		UUID id,
		String label,
		LoanTerms terms,
		LocalDate firstDueDate,
		UUID depositAccountId) {
}
