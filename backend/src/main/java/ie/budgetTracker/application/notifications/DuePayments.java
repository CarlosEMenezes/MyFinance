package ie.budgetTracker.application.notifications;

import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.financing.FinancingRepository;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.StatementCycleCalculator;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.notifications.DuePayment;
import ie.budgetTracker.domain.notifications.DueSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What is falling due, assembled once (BR-12).
 *
 * Derived every time it is asked, never stored: a paid card drops out of the
 * queue on its own, and there is no row for anything to forget to delete.
 *
 * Shared by the dashboard's upcoming panel and the notifications queue. Two
 * assemblies would eventually disagree, and a warning that appears in one
 * place and not the other is worse than one that appears in neither.
 *
 * Direct debits and subscriptions are two of BR-12's five sources and have no
 * data yet: they arrive with recurrence in Phase 2 (BR-16). Their absence is
 * an absence of rows, not a gap in the rule - nothing here would need changing
 * to include them.
 */
@Component
public class DuePayments {

	private final CardRepository cards;
	private final FinancingRepository financing;

	public DuePayments(CardRepository cards, FinancingRepository financing) {
		this.cards = cards;
		this.financing = financing;
	}

	/** Everything owed by this user, nearest first. */
	public List<DuePayment> forUser(UUID userId, LocalDate today) {
		List<DuePayment> due = new ArrayList<>();

		for (Card card : cards.findAllForUser(userId)) {
			// A cleared card has no bill to warn about.
			if (card instanceof CreditCard credit
					&& MoneyCalculator.isPositive(credit.currentBalance())) {
				due.add(new DuePayment("card-" + credit.id(), credit.name() + " card payment",
						"statement closes day " + credit.cycle().closingDay(),
						StatementCycleCalculator.nextDueDateOnOrAfter(today,
								credit.cycle().dueDay()),
						credit.currentBalance(), DueSource.CARD_BILL));
			}
		}

		for (InstalmentPlan plan : financing.findPlansForUser(userId)) {
			if (plan.instalmentsRemaining() > 0) {
				due.add(new DuePayment("instalment-" + plan.id(), plan.label(),
						plan.instalmentsRemaining() + " instalments left", plan.firstDueDate(),
						plan.terms().instalmentAmount(), DueSource.INSTALMENT));
			}
		}

		for (Loan loan : financing.findLoansForUser(userId)) {
			if (loan.terms().instalmentsRemaining() > 0) {
				due.add(new DuePayment("loan-" + loan.id(), loan.label(),
						loan.terms().instalmentsRemaining() + " instalments left",
						loan.firstDueDate(), loan.terms().instalmentAmount(), DueSource.LOAN));
			}
		}

		return due.stream().sorted(Comparator.comparing(DuePayment::dueDate)).toList();
	}
}
