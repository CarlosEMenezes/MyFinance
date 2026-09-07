package ie.budgetTracker.application.financing;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.financing.dto.CreateLoanRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPlanResponse;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewResponse;
import ie.budgetTracker.application.financing.dto.InterestSummaryResponse;
import ie.budgetTracker.application.financing.dto.LoanPreviewRequest;
import ie.budgetTracker.application.financing.dto.LoanPreviewResponse;
import ie.budgetTracker.application.financing.dto.LoanResponse;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.StatementCycleCalculator;
import ie.budgetTracker.domain.financing.InstalmentCalculator;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanAnalysis;
import ie.budgetTracker.domain.financing.LoanCalculator;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.plan.Frequency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Instalment plans and loans (BR-6, BR-7, BR-2).
 *
 * Every interest figure on every response is solved here, from the terms, at
 * the moment it is asked for. Nothing is stored: BR-7's settlement figure
 * changes with each instalment paid, and a stored APR would be a number that
 * was true once, sitting beside terms that have moved on.
 *
 * The preview endpoints answer the same questions without writing anything, so
 * the log form can show a figure for money not yet committed - and so the
 * frontend's own pure functions have something authoritative to agree with
 * (ADR-7).
 */
@Service
public class FinancingService {

	private final FinancingRepository financing;
	private final CardRepository cards;
	private final CurrentUser currentUser;

	public FinancingService(FinancingRepository financing, CardRepository cards,
			CurrentUser currentUser) {
		this.financing = financing;
		this.cards = cards;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public List<InstalmentPlanResponse> plans() {
		return financing.findPlansForUser(currentUser.id()).stream()
				.map(plan -> InstalmentPlanResponse.from(plan,
						InstalmentCalculator.analyse(plan.terms())))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LoanResponse> loans() {
		return financing.findLoansForUser(currentUser.id()).stream()
				.map(loan -> LoanResponse.from(loan, LoanCalculator.analyse(loan.terms())))
				.toList();
	}

	/**
	 * BR-2: records the principal, the terms and the deposit account together.
	 *
	 * One write, because a loan whose principal landed but whose repayment did
	 * not exist yet would show as income for as long as the gap lasted.
	 */
	@Transactional
	public LoanResponse createLoan(CreateLoanRequest request) {
		LoanTerms terms = new LoanTerms(
				Money.fromMinorUnits(request.principal()),
				request.instalmentCount(),
				Money.fromMinorUnits(request.instalmentAmount()),
				request.frequency(),
				request.instalmentsPaid() == null ? 0 : request.instalmentsPaid());

		Loan written = financing.createLoan(currentUser.id(), new Loan(null,
				request.label().trim(), terms, request.firstDueDate(),
				request.depositAccountId()));

		return LoanResponse.from(written, LoanCalculator.analyse(written.terms()));
	}

	/**
	 * BR-6 and BR-4 together, for a purchase not yet made.
	 *
	 * The first instalment lands on a bill, so the card's cycle decides the date
	 * as surely as the terms decide the interest.
	 */
	@Transactional(readOnly = true)
	public InstalmentPreviewResponse previewInstalments(InstalmentPreviewRequest request) {
		InstalmentTerms terms = terms(request.cashPrice(), request.instalmentCount(),
				request.instalmentAmount(), request.frequency());

		return new InstalmentPreviewResponse(
				InterestSummaryResponse.from(InstalmentCalculator.analyse(terms)),
				firstDueDate(creditCard(request.cardId()), request.purchaseDate()));
	}

	/** BR-7 and BR-2, for money not yet borrowed. */
	public LoanPreviewResponse previewLoan(LoanPreviewRequest request) {
		LoanAnalysis analysis = LoanCalculator.analyse(new LoanTerms(
				Money.fromMinorUnits(request.principal()),
				request.instalmentCount(),
				Money.fromMinorUnits(request.instalmentAmount()),
				request.frequency(),
				0));

		// Both sides, always. Showing only what a loan adds to what is available
		// would make borrowing look like income (BR-2).
		return new LoanPreviewResponse(
				InterestSummaryResponse.from(analysis.interest()),
				Money.toMinorUnits(analysis.addsToAvailable()),
				Money.toMinorUnits(analysis.addsToOwed()),
				Money.toMinorUnits(analysis.settlementFigureToday()));
	}

	/**
	 * BR-6: the plan behind a financed purchase, written with the transaction.
	 *
	 * Called by {@code TransactionService} inside the same transaction, so a
	 * purchase and the plan that spreads it are one atomic write (spec §4).
	 */
	public InstalmentPlan planFor(UUID cardId, String label, long cashPrice,
			int instalmentCount, long instalmentAmount, Frequency frequency,
			LocalDate purchaseDate) {

		CreditCard card = creditCard(cardId);

		return financing.createPlan(currentUser.id(), new InstalmentPlan(null, card.id(), label,
				terms(cashPrice, instalmentCount, instalmentAmount, frequency), 0,
				firstDueDate(card, purchaseDate)));
	}

	private static InstalmentTerms terms(long cashPrice, int count, long amount,
			Frequency frequency) {
		return new InstalmentTerms(Money.fromMinorUnits(cashPrice), count,
				Money.fromMinorUnits(amount), frequency);
	}

	private static LocalDate firstDueDate(CreditCard card, LocalDate purchaseDate) {
		return StatementCycleCalculator.billDateFor(purchaseDate, card.cycle());
	}

	/**
	 * BR-5: a debit card cannot carry instalments, because there is no statement
	 * for them to land on.
	 */
	private CreditCard creditCard(UUID cardId) {
		Card card = cards.findForUser(currentUser.id(), cardId)
				.orElseThrow(() -> AppException.notFound("No card with id " + cardId));

		if (!(card instanceof CreditCard credit)) {
			throw AppException.invalid("cardId", "\"" + card.name()
					+ "\" is a debit card, and its spending leaves the account the same day, "
					+ "so it cannot carry instalments (BR-5)");
		}
		return credit;
	}
}
