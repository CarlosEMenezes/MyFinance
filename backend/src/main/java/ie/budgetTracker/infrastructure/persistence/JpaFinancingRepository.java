package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.financing.FinancingRepository;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.Loan;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link FinancingRepository}. */
@Repository
class JpaFinancingRepository implements FinancingRepository {

	private final InstalmentPlanJpaRepository plans;
	private final LoanJpaRepository loans;
	private final CardJpaRepository cards;
	private final AccountJpaRepository accounts;

	JpaFinancingRepository(InstalmentPlanJpaRepository plans, LoanJpaRepository loans,
			CardJpaRepository cards, AccountJpaRepository accounts) {
		this.plans = plans;
		this.loans = loans;
		this.cards = cards;
		this.accounts = accounts;
	}

	@Override
	public List<InstalmentPlan> findPlansForUser(UUID userId) {
		return plans.findByCardAccountUserIdOrderByFirstDueDateAsc(userId).stream()
				.map(InstalmentPlanEntity::toDomain)
				.toList();
	}

	@Override
	public List<Loan> findLoansForUser(UUID userId) {
		return loans.findByDepositAccountUserIdOrderByFirstDueDateAsc(userId).stream()
				.map(LoanEntity::toDomain)
				.toList();
	}

	@Override
	public InstalmentPlan createPlan(UUID userId, InstalmentPlan plan) {
		// By owner AND id: a card belonging to somebody else is not found here
		// rather than found and then refused (ADR-11).
		CardEntity card = cards.findByAccountUserIdAndId(userId, plan.cardId())
				.orElseThrow(() -> AppException.notFound("No card with id " + plan.cardId()));

		return plans.save(new InstalmentPlanEntity(card, plan)).toDomain();
	}

	@Override
	public Loan createLoan(UUID userId, Loan loan) {
		AccountEntity account = accounts.findByUserIdAndId(userId, loan.depositAccountId())
				.orElseThrow(() -> AppException.notFound(
						"No account with id " + loan.depositAccountId()));

		return loans.save(new LoanEntity(account, loan)).toDomain();
	}
}
