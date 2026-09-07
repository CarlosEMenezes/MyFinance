package ie.budgetTracker.application.dashboard;

import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.dashboard.dto.DashboardResponse;
import ie.budgetTracker.application.dashboard.dto.PlanRowResponse;
import ie.budgetTracker.application.financing.FinancingRepository;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.notifications.DuePayments;
import ie.budgetTracker.application.plan.CategoryRepository;
import ie.budgetTracker.application.plan.PeriodWindows;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.application.transactions.TransactionRepository;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.DateRange;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.PlanNormaliser;
import ie.budgetTracker.domain.plan.Variance;
import ie.budgetTracker.domain.plan.VarianceCalculator;
import ie.budgetTracker.domain.plan.VarianceTone;
import ie.budgetTracker.domain.position.AccountBalance;
import ie.budgetTracker.domain.position.OutstandingCommitment;
import ie.budgetTracker.domain.position.Position;
import ie.budgetTracker.domain.position.PositionCalculator;
import ie.budgetTracker.domain.position.PositionInputs;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole overview, computed once, on this side (spec §4).
 *
 * Overview, Earnings and Expenses are three readings of the same period. Served
 * from three calculations they would eventually disagree about what August
 * contained, and nothing on screen would say which was right - so there is one
 * call, and the frontend recomputes none of it (ADR-7).
 *
 * Two rules decide how figures land here, and they are easy to confuse:
 *
 *   - **A category plan is counted on real dates** (BR-10), so a month holding
 *     five paydays plans five.
 *   - **A derived row is averaged at 52/12** (BR-3), because a standing
 *     commitment answers "what does this cost me a month" rather than "what
 *     falls due in August".
 *
 * See CLAUDE.md gotcha 5. They must not be unified.
 */
@Service
public class DashboardService {

	/** Enough to fill the Overview panel without becoming a second page. */
	private static final int UPCOMING_LIMIT = 6;

	private static final int PERCENT = 100;

	private final PeriodWindows periods;
	private final CategoryRepository categories;
	private final TransactionRepository transactions;
	private final AccountRepository accounts;
	private final CardRepository cards;
	private final CardService cardNames;
	private final FinancingRepository financing;
	private final DuePayments duePayments;
	private final CurrentUser currentUser;
	private final Clock clock;

	public DashboardService(PeriodWindows periods, CategoryRepository categories,
			TransactionRepository transactions, AccountRepository accounts, CardRepository cards,
			CardService cardNames, FinancingRepository financing, DuePayments duePayments,
			CurrentUser currentUser, Clock clock) {
		this.periods = periods;
		this.categories = categories;
		this.transactions = transactions;
		this.accounts = accounts;
		this.cards = cards;
		this.cardNames = cardNames;
		this.financing = financing;
		this.duePayments = duePayments;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardResponse forPeriod(String period, LocalDate from, LocalDate to) {
		UUID user = currentUser.id();
		PeriodWindowResponse window = periods.describe(period, from, to);
		DateRange range = new DateRange(window.from(), window.to());

		List<Transaction> logged = transactions.findForUserInPeriod(user, window.from(),
				window.to());
		Map<UUID, List<Transaction>> byCategory = logged.stream()
				.collect(Collectors.groupingBy(Transaction::categoryId, LinkedHashMap::new,
						Collectors.toList()));

		List<Account> ownedAccounts = accounts.findAllForUser(user);
		List<Card> ownedCards = cards.findAllForUser(user);
		List<InstalmentPlan> plans = financing.findPlansForUser(user);
		List<Loan> loans = financing.findLoansForUser(user);

		Map<UUID, String> methodNames = methodNames(ownedAccounts, ownedCards);

		List<PlanRowResponse> earnings = rowsOf(CategoryType.EARNING, user, range, byCategory,
				methodNames);
		List<PlanRowResponse> expenses = new ArrayList<>(
				rowsOf(CategoryType.EXPENSE, user, range, byCategory, methodNames));
		expenses.addAll(derivedRows(plans, loans, ownedCards, ownedAccounts, methodNames));

		return new DashboardResponse(
				window,
				position(ownedAccounts, ownedCards, plans, loans, logged),
				earnings,
				List.copyOf(expenses),
				totals(earnings, expenses),
				upcoming(user),
				categorySpend(expenses),
				accountsWithTheirCards(ownedAccounts));
	}

	/* ── the plan-vs-real rows ─────────────────────────────────────────── */

	private List<PlanRowResponse> rowsOf(CategoryType type, UUID user, DateRange range,
			Map<UUID, List<Transaction>> byCategory, Map<UUID, String> methodNames) {

		return categories.findAllForUser(user).stream()
				.filter(category -> category.type() == type && !category.archived())
				.map(category -> row(category, range,
						byCategory.getOrDefault(category.id(), List.of()), methodNames))
				.toList();
	}

	private static PlanRowResponse row(Category category, DateRange range,
			List<Transaction> logged, Map<UUID, String> methodNames) {

		// BR-10: counted on real dates, so a month holding five paydays plans
		// five. Never the 52/12 average - that belongs to the derived rows.
		BigDecimal planned = PlanNormaliser.plannedAmountIn(category.plannedAmount(),
				category.plannedFrequency(), category.anchorDate(), range);
		BigDecimal real = MoneyCalculator.sum(logged.stream()
				.map(Transaction::amountInDefaultCurrency)
				.toList());

		Variance variance = VarianceCalculator.varianceOf(category.type(), planned, real);

		return new PlanRowResponse(
				category.id().toString(),
				category.name(),
				category.type(),
				category.group(),
				Money.toMinorUnits(planned),
				Money.toMinorUnits(real),
				Money.toMinorUnits(variance.amount()),
				variance.tone(),
				Money.toMinorUnits(category.plannedAmount()),
				category.plannedFrequency(),
				PlanNormaliser.occurrencesIn(category.plannedFrequency(), category.anchorDate(),
						range),
				Money.toMinorUnits(MoneyCalculator.multiply(category.plannedAmount(),
						category.plannedFrequency().periodsPerMonth())),
				false,
				onlyName(logged.stream().map(entry -> methodNames.get(entry.paymentMethod().id()))
						.collect(Collectors.toSet())),
				null,
				foreignAmount(logged));
	}

	/**
	 * BR-8: what was typed, when it was not in the default currency.
	 *
	 * Stated only when every foreign entry in the row shares one currency. Two
	 * currencies added together would be a number in no currency at all, and a
	 * row that showed one of them would be quietly hiding the other.
	 */
	private static String foreignAmount(List<Transaction> logged) {
		List<Transaction> foreign = logged.stream()
				.filter(entry -> entry.fxRate().compareTo(BigDecimal.ONE) != 0)
				.toList();

		Set<String> currencies = foreign.stream()
				.map(entry -> entry.currency().name())
				.collect(Collectors.toSet());

		if (foreign.isEmpty() || currencies.size() != 1) {
			return null;
		}

		return currencies.iterator().next() + " " + MoneyCalculator.sum(
				foreign.stream().map(Transaction::amount).toList()).toPlainString();
	}

	/**
	 * A name only when there is exactly one, because "several" is not a filter.
	 *
	 * The Expenses page filters on this exact string (BR-15), so naming one of
	 * two methods would make a filter hide rows it should show.
	 */
	private static String onlyName(Set<String> names) {
		Set<String> named = names.stream()
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());

		return named.size() == 1 ? named.iterator().next() : null;
	}

	/**
	 * BR-3: the read-only rows, averaged at 52/12.
	 *
	 * Planned and real are the same figure, so the variance is zero and the tone
	 * neutral: a standing commitment is not something you can over- or underspend
	 * against. BR-14 renders these as text, never as an input, and `derived`
	 * carries that instruction to the screen.
	 */
	private static List<PlanRowResponse> derivedRows(List<InstalmentPlan> plans, List<Loan> loans,
			List<Card> cards, List<Account> accounts, Map<UUID, String> methodNames) {

		List<PlanRowResponse> rows = new ArrayList<>();

		BigDecimal instalments = PositionCalculator.monthlyCommitment(plans.stream()
				.map(plan -> new OutstandingCommitment(plan.terms().instalmentAmount(),
						plan.instalmentsRemaining(), plan.terms().frequency()))
				.toList());

		if (MoneyCalculator.isPositive(instalments)) {
			rows.add(derivedRow("card-instalments", "Card instalments", instalments,
					onlyName(plans.stream()
							.filter(plan -> plan.instalmentsRemaining() > 0)
							.map(plan -> methodNames.get(plan.cardId()))
							.collect(Collectors.toSet()))));
		}

		BigDecimal repayments = PositionCalculator.monthlyCommitment(loans.stream()
				.map(loan -> new OutstandingCommitment(loan.terms().instalmentAmount(),
						loan.terms().instalmentsRemaining(), loan.terms().frequency()))
				.toList());

		if (MoneyCalculator.isPositive(repayments)) {
			rows.add(derivedRow("loan-repayments", "Loan repayments", repayments,
					onlyName(loans.stream()
							.filter(loan -> loan.terms().instalmentsRemaining() > 0)
							.map(loan -> methodNames.get(loan.depositAccountId()))
							.collect(Collectors.toSet()))));
		}

		return rows;
	}

	private static PlanRowResponse derivedRow(String id, String label, BigDecimal monthly,
			String paidWith) {
		long amount = Money.toMinorUnits(monthly);

		return new PlanRowResponse(id, label, CategoryType.EXPENSE, "Debt", amount, amount, 0L,
				VarianceTone.NEUTRAL, amount, Frequency.MONTHLY, 1, amount, true, paidWith,
				// Deliberately no due note: the upcoming queue already carries these
				// dates, and a second copy of them could disagree with the first.
				null, null);
	}

	/* ── the position ──────────────────────────────────────────────────── */

	/**
	 * BR-1 and BR-2, assembled from what is on the books.
	 *
	 * A loan's principal counts toward what is available only while instalments
	 * remain. A settled loan is off the books on both sides, and leaving the
	 * principal in would show money that was spent years ago.
	 */
	private static DashboardResponse.PositionResponse position(List<Account> accounts,
			List<Card> cards, List<InstalmentPlan> plans, List<Loan> loans,
			List<Transaction> logged) {

		BigDecimal cardBalances = MoneyCalculator.sum(cards.stream()
				.filter(CreditCard.class::isInstance)
				.map(card -> ((CreditCard) card).currentBalance())
				.toList());

		BigDecimal borrowed = MoneyCalculator.sum(loans.stream()
				.filter(loan -> loan.terms().instalmentsRemaining() > 0)
				.map(loan -> loan.terms().principal())
				.toList());

		BigDecimal earned = MoneyCalculator.sum(logged.stream()
				.filter(entry -> entry.type() == TransactionType.EARNING)
				.map(Transaction::amountInDefaultCurrency)
				.toList());

		// BR-1 excludes credit-card spending here: it has not left an account,
		// and it is already counted on the other side as part of what is owed.
		BigDecimal spentFromAccounts = MoneyCalculator.sum(logged.stream()
				.filter(entry -> entry.type() != TransactionType.EARNING)
				.filter(entry -> entry.paymentMethod().kind() != PaymentMethodKind.CREDIT_CARD)
				.map(Transaction::amountInDefaultCurrency)
				.toList());

		Position position = PositionCalculator.calculate(new PositionInputs(
				accounts.stream()
						.map(account -> new AccountBalance(account.balance(),
								account.includeInTotals()))
						.toList(),
				cardBalances,
				plans.stream()
						.map(plan -> new OutstandingCommitment(plan.terms().instalmentAmount(),
								plan.instalmentsRemaining(), plan.terms().frequency()))
						.toList(),
				loans.stream()
						.map(loan -> new OutstandingCommitment(loan.terms().instalmentAmount(),
								loan.terms().instalmentsRemaining(), loan.terms().frequency()))
						.toList(),
				borrowed, earned, spentFromAccounts));

		return new DashboardResponse.PositionResponse(
				Money.toMinorUnits(position.totalMoneyNow()),
				Money.toMinorUnits(position.availableNow()),
				Money.toMinorUnits(position.owed()),
				Money.toMinorUnits(position.owedOnCards()),
				Money.toMinorUnits(position.owedOnInstalments()),
				Money.toMinorUnits(position.owedOnLoans()),
				Money.toMinorUnits(position.borrowed()));
	}

	/* ── totals, upcoming and the spending breakdown ───────────────────── */

	/** BR-15: these cover the whole period, whatever a screen chooses to show. */
	private static DashboardResponse.Totals totals(List<PlanRowResponse> earnings,
			List<PlanRowResponse> expenses) {

		long earningsPlanned = earnings.stream().mapToLong(PlanRowResponse::planned).sum();
		long earningsReal = earnings.stream().mapToLong(PlanRowResponse::real).sum();
		long expensesPlanned = expenses.stream().mapToLong(PlanRowResponse::planned).sum();
		long expensesReal = expenses.stream().mapToLong(PlanRowResponse::real).sum();

		return new DashboardResponse.Totals(earningsPlanned, earningsReal, expensesPlanned,
				expensesReal, earningsPlanned - expensesPlanned, earningsReal - expensesReal);
	}

	/**
	 * BR-12: what falls due next, nearest first.
	 *
	 * Assembled by the same component the notifications queue uses. Two
	 * assemblies would eventually disagree, and a warning that appears on one
	 * screen and not the other is worse than one that appears on neither.
	 */
	private List<DashboardResponse.UpcomingPaymentResponse> upcoming(UUID user) {
		return duePayments.forUser(user, LocalDate.now(clock)).stream()
				.limit(UPCOMING_LIMIT)
				// Negative for money leaving: every one of these is a payment.
				.map(payment -> new DashboardResponse.UpcomingPaymentResponse(payment.key(),
						payment.label(), payment.detail(), payment.dueDate(),
						-Money.toMinorUnits(payment.amount())))
				.toList();
	}

	/**
	 * The spending breakdown, already scaled.
	 *
	 * Scaled against the largest real figure so the bars need no arithmetic on
	 * the screen. A planned bar may exceed 100%, which is the point: it says the
	 * plan was larger than the biggest thing actually spent.
	 */
	private static List<DashboardResponse.CategorySpendResponse> categorySpend(
			List<PlanRowResponse> expenses) {

		List<PlanRowResponse> spent = expenses.stream()
				.filter(row -> row.real() > 0)
				.sorted(Comparator.comparingLong(PlanRowResponse::real).reversed())
				.toList();

		if (spent.isEmpty()) {
			return List.of();
		}

		long largest = spent.get(0).real();

		return spent.stream()
				.map(row -> new DashboardResponse.CategorySpendResponse(row.categoryId(),
						row.category(), row.real(), row.planned(),
						percentOf(row.real(), largest), percentOf(row.planned(), largest)))
				.toList();
	}

	private static int percentOf(long amount, long largest) {
		return largest == 0 ? 0 : Math.toIntExact(Math.round(amount * (double) PERCENT / largest));
	}

	private List<AccountResponse> accountsWithTheirCards(List<Account> owned) {
		Map<UUID, List<String>> byAccount = cardNames.cardNamesByAccount();

		return owned.stream()
				.map(account -> AccountResponse.from(account,
						byAccount.getOrDefault(account.id(), List.of())))
				.toList();
	}

	/** Every payment method this user has, by id, so a row can name one. */
	private static Map<UUID, String> methodNames(List<Account> accounts, List<Card> cards) {
		Map<UUID, String> names = new LinkedHashMap<>();
		accounts.forEach(account -> names.put(account.id(), account.name()));
		cards.forEach(card -> names.put(card.id(), card.name()));
		return names;
	}
}
