package ie.budgetTracker.application.dashboard;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.dashboard.dto.DashboardResponse;
import ie.budgetTracker.application.dashboard.dto.PlanRowResponse;
import ie.budgetTracker.application.financing.FinancingRepository;
import ie.budgetTracker.application.notifications.DuePayments;
import ie.budgetTracker.application.plan.CategoryRepository;
import ie.budgetTracker.application.plan.PeriodWindows;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.transactions.TransactionRepository;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.PeriodKind;
import ie.budgetTracker.domain.plan.VarianceTone;
import ie.budgetTracker.domain.transactions.PaymentMethod;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The whole overview, assembled from mocked ports.
 *
 * August 2026 is the window throughout, because it is the month the prototype
 * was drawn against and the one that holds five weekly paydays from a 5 January
 * anchor - which is the whole reason BR-10 counts real dates.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID REVOLUT = UUID.randomUUID();
	private static final UUID VISA = UUID.randomUUID();
	private static final UUID GROCERIES = UUID.randomUUID();
	private static final UUID TUTORING = UUID.randomUUID();

	@Mock
	private PeriodWindows periods;

	@Mock
	private CategoryRepository categories;

	@Mock
	private TransactionRepository transactions;

	@Mock
	private AccountRepository accounts;

	@Mock
	private CardRepository cards;

	@Mock
	private CardService cardNames;

	@Mock
	private FinancingRepository financing;

	/**
	 * The real assembler, over the same mocked ports.
	 *
	 * Mocking it would leave the upcoming panel proving only that a list was
	 * copied; the point of these tests is which payments actually appear.
	 */
	private DuePayments duePayments;

	private DashboardService service;

	@BeforeEach
	void setUp() {
		duePayments = new DuePayments(cards, financing);
		service = new DashboardService(periods, categories, transactions, accounts, cards,
				cardNames, financing, duePayments, () -> ADA, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void august() {
		given(periods.describe(any(), any(), any())).willReturn(new PeriodWindowResponse(
				PeriodKind.MONTH, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"),
				"August 2026"));
	}

	private static Category groceries() {
		return new Category(GROCERIES, CategoryType.EXPENSE, "Groceries", "Variable",
				of("100.00"), Frequency.WEEKLY, LocalDate.parse("2026-01-03"), false);
	}

	private static Category tutoring() {
		return new Category(TUTORING, CategoryType.EARNING, "Tutoring", "Self-employed",
				of("160.00"), Frequency.WEEKLY, LocalDate.parse("2026-01-05"), false);
	}

	private static Transaction spent(String amount, LocalDate on, PaymentMethodKind kind,
			UUID method) {
		return new Transaction(UUID.randomUUID(), TransactionType.EXPENSE, GROCERIES,
				of(amount), Currency.EUR, of(amount), BigDecimal.ONE, on,
				new PaymentMethod(method, kind), null, null, null, null);
	}

	private static Account revolut(String balance, boolean includeInTotals) {
		return new Account(REVOLUT, "Revolut Current", AccountKind.BANK, of(balance),
				Currency.EUR, includeInTotals, null, List.of());
	}

	private static CreditCard visa(String owed) {
		return new CreditCard(VISA, "Visa 4417", REVOLUT, of("2000.00"), of(owed),
				new StatementCycle(25, 5));
	}

	private DashboardResponse dashboard() {
		return service.forPeriod("MONTH", null, null);
	}

	@Nested
	@DisplayName("the plan-vs-real rows")
	class Rows {

		@Test
		@DisplayName("BR-10: counts the plan on real dates, not on an average")
		void countsThePlanOnRealDates() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(tutoring()));

			PlanRowResponse row = dashboard().earnings().get(0);

			// 160.00 weekly, anchored to a Monday, in a month that holds five of
			// them. An averaged month would say four and be 160.00 short.
			assertThat(row.occurrencesInPeriod()).isEqualTo(5);
			assertThat(row.planned()).isEqualTo(80000L);
			assertThat(row.perOccurrence()).isEqualTo(16000L);
		}

		@Test
		@DisplayName("BR-3: states the averaged monthly figure beside the real one")
		void statesTheAveragedMonthlyFigureToo() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(tutoring()));

			// 160.00 x 52/12 = 693.33. A different question from BR-10's 800.00,
			// and both are on the row so neither has to stand in for the other.
			assertThat(dashboard().earnings().get(0).monthlyEquivalent()).isEqualTo(69333L);
		}

		@Test
		@DisplayName("BR-9: resolves the variance and its tone, so no screen decides")
		void resolvesTheVarianceAndItsTone() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					spent("600.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.ACCOUNT,
							REVOLUT)));

			PlanRowResponse row = dashboard().expenses().get(0);

			// 100.00 weekly lands five times in August, so the plan is 500.00 and
			// 600.00 spent is 100.00 over. The variance is `real - planned` on both
			// sides of the plan; only the tone differs, and overspending on an
			// expense is bad news.
			assertThat(row.planned()).isEqualTo(50000L);
			assertThat(row.real()).isEqualTo(60000L);
			assertThat(row.variance()).isEqualTo(10000L);
			assertThat(row.varianceTone()).isEqualTo(VarianceTone.BAD);
		}

		@Test
		@DisplayName("BR-9: spending under the plan is the same sign, a different tone")
		void underspendingKeepsTheSignAndChangesTheTone() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					spent("400.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.ACCOUNT,
							REVOLUT)));

			PlanRowResponse row = dashboard().expenses().get(0);

			// Negative, not flipped positive. One sign convention is what lets a
			// column of variances be summed without asking what kind each row is.
			assertThat(row.variance()).isEqualTo(-10000L);
			assertThat(row.varianceTone()).isEqualTo(VarianceTone.GOOD);
		}

		@Test
		@DisplayName("BR-8: names the original amount when a row was logged abroad")
		void namesTheOriginalAmountWhenLoggedAbroad() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					new Transaction(UUID.randomUUID(), TransactionType.EXPENSE, GROCERIES,
							of("100.00"), Currency.USD, of("92.10"), new BigDecimal("0.921"),
							LocalDate.parse("2026-08-05"),
							new PaymentMethod(REVOLUT, PaymentMethodKind.ACCOUNT), null, null,
							null, null)));

			PlanRowResponse row = dashboard().expenses().get(0);

			// The total is in the default currency; the tag says what was typed.
			assertThat(row.real()).isEqualTo(9210L);
			assertThat(row.foreignAmount()).isEqualTo("USD 100.00");
		}

		@Test
		@DisplayName("BR-8: two foreign currencies in one row are named as neither")
		void twoForeignCurrenciesAreNamedAsNeither() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					new Transaction(UUID.randomUUID(), TransactionType.EXPENSE, GROCERIES,
							of("100.00"), Currency.USD, of("92.10"), new BigDecimal("0.921"),
							LocalDate.parse("2026-08-05"),
							new PaymentMethod(REVOLUT, PaymentMethodKind.ACCOUNT), null, null,
							null, null),
					new Transaction(UUID.randomUUID(), TransactionType.EXPENSE, GROCERIES,
							of("50.00"), Currency.GBP, of("59.37"), new BigDecimal("1.1874"),
							LocalDate.parse("2026-08-06"),
							new PaymentMethod(REVOLUT, PaymentMethodKind.ACCOUNT), null, null,
							null, null)));

			// Adding dollars to pounds would be a number in no currency at all,
			// and showing one of them would quietly hide the other.
			assertThat(dashboard().expenses().get(0).foreignAmount()).isNull();
		}

		@Test
		@DisplayName("leaves archived categories out of the tables")
		void leavesArchivedCategoriesOut() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(new Category(GROCERIES,
					CategoryType.EXPENSE, "Old gym", "Fixed", of("40.00"), Frequency.MONTHLY,
					LocalDate.parse("2026-01-01"), true)));

			assertThat(dashboard().expenses()).isEmpty();
		}
	}

	@Nested
	@DisplayName("BR-3, the derived rows")
	class DerivedRows {

		@Test
		@DisplayName("BR-3, BR-14: a loan becomes a read-only row, averaged at 52/12")
		void aLoanBecomesAReadOnlyRow() {
			august();
			given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("500.00", true)));
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Credit union", new LoanTerms(of("2500.00"), 24, of("118.40"),
							Frequency.MONTHLY, 5),
					LocalDate.parse("2026-09-01"), REVOLUT)));

			PlanRowResponse row = dashboard().expenses().stream()
					.filter(PlanRowResponse::derived)
					.findFirst()
					.orElseThrow();

			assertThat(row.category()).isEqualTo("Loan repayments");
			assertThat(row.planned()).isEqualTo(11840L);
			// Planned and real are the same figure, so there is nothing to be
			// over or under: a standing commitment is not overspent against.
			assertThat(row.real()).isEqualTo(11840L);
			assertThat(row.variance()).isZero();
			assertThat(row.varianceTone()).isEqualTo(VarianceTone.NEUTRAL);
			assertThat(row.paidWith()).isEqualTo("Revolut Current");
		}

		@Test
		@DisplayName("BR-3: a card plan becomes its own row, named for its card")
		void aCardPlanBecomesItsOwnRow() {
			august();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa("0.00")));
			given(financing.findPlansForUser(ADA)).willReturn(List.of(new InstalmentPlan(
					UUID.randomUUID(), VISA, "Laptop",
					new InstalmentTerms(of("399.00"), 6, of("71.50"), Frequency.MONTHLY), 0,
					LocalDate.parse("2026-09-05"))));

			PlanRowResponse row = dashboard().expenses().get(0);

			assertThat(row.category()).isEqualTo("Card instalments");
			assertThat(row.derived()).isTrue();
			assertThat(row.planned()).isEqualTo(7150L);
			assertThat(row.paidWith()).isEqualTo("Visa 4417");
		}

		@Test
		@DisplayName("BR-3: a settled loan leaves no row behind")
		void aSettledLoanLeavesNoRow() {
			august();
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Family", new LoanTerms(of("600.00"), 6, of("100.00"), Frequency.MONTHLY, 6),
					LocalDate.parse("2026-01-01"), REVOLUT)));

			// Nothing remains to repay, so there is nothing to plan for.
			assertThat(dashboard().expenses()).isEmpty();
		}
	}

	@Nested
	@DisplayName("BR-1 and BR-2, the position")
	class TheePosition {

		@Test
		@DisplayName("BR-1: adds what was earned and subtracts what left an account")
		void addsEarningsAndSubtractsAccountSpending() {
			august();
			given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("500.00", true)));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					new Transaction(UUID.randomUUID(), TransactionType.EARNING, TUTORING,
							of("160.00"), Currency.EUR, of("160.00"), BigDecimal.ONE,
							LocalDate.parse("2026-08-03"),
							new PaymentMethod(REVOLUT, PaymentMethodKind.ACCOUNT), null, null,
							null, null),
					spent("60.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.ACCOUNT,
							REVOLUT)));

			assertThat(dashboard().position().availableNow()).isEqualTo(60000L);
		}

		@Test
		@DisplayName("BR-1: card spending is not subtracted, because it is already owed")
		void cardSpendingIsNotSubtractedTwice() {
			august();
			given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("500.00", true)));
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa("50.00")));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					spent("50.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.CREDIT_CARD,
							VISA)));

			DashboardResponse.PositionResponse position = dashboard().position();

			// The fifty appears once, as what is owed on the card. Subtracting it
			// from what is available as well would charge it twice.
			assertThat(position.availableNow()).isEqualTo(50000L);
			assertThat(position.owedOnCards()).isEqualTo(5000L);
			assertThat(position.totalMoneyNow()).isEqualTo(45000L);
		}

		@Test
		@DisplayName("BR-2: borrowing moves both sides, and nets out to the interest")
		void borrowingMovesBothSides() {
			august();
			given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("0.00", true)));
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Credit union", new LoanTerms(of("2500.00"), 24, of("118.40"),
							Frequency.MONTHLY, 0),
					LocalDate.parse("2026-09-01"), REVOLUT)));

			DashboardResponse.PositionResponse position = dashboard().position();

			assertThat(position.borrowed()).isEqualTo(250000L);
			assertThat(position.availableNow()).isEqualTo(250000L);
			assertThat(position.owedOnLoans()).isEqualTo(284160L);
			// 2,500 in and 2,841.60 owed: the lasting effect is the 341.60 of
			// interest, and nothing else.
			assertThat(position.totalMoneyNow()).isEqualTo(-34160L);
		}

		@Test
		@DisplayName("BR-13: an account kept out of totals is out of the position too")
		void anExcludedAccountIsOutOfThePosition() {
			august();
			given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("5000.00", false)));

			assertThat(dashboard().position().availableNow()).isZero();
		}
	}

	@Nested
	@DisplayName("BR-15, the totals and the breakdown")
	class TotalsAndBreakdown {

		@Test
		@DisplayName("BR-15: the totals cover the whole period, filtered or not")
		void totalsCoverTheWholePeriod() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries(), tutoring()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					spent("120.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.ACCOUNT,
							REVOLUT)));

			DashboardResponse.Totals totals = dashboard().totals();

			assertThat(totals.earningsPlanned()).isEqualTo(80000L);
			assertThat(totals.expensesReal()).isEqualTo(12000L);
			assertThat(totals.netPlanned())
					.isEqualTo(totals.earningsPlanned() - totals.expensesPlanned());
			assertThat(totals.netReal()).isEqualTo(totals.earningsReal() - totals.expensesReal());
		}

		@Test
		@DisplayName("scales the breakdown against the largest row, so a bar needs no maths")
		void scalesTheBreakdownAgainstTheLargestRow() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));
			given(transactions.findForUserInPeriod(any(), any(), any())).willReturn(List.of(
					spent("120.00", LocalDate.parse("2026-08-05"), PaymentMethodKind.ACCOUNT,
							REVOLUT)));

			DashboardResponse.CategorySpendResponse bar = dashboard().categorySpend().get(0);

			assertThat(bar.label()).isEqualTo("Groceries");
			assertThat(bar.percentOfLargest()).isEqualTo(100);
		}

		@Test
		void leavesCategoriesWithNothingSpentOffTheBreakdown() {
			august();
			given(categories.findAllForUser(ADA)).willReturn(List.of(groceries()));

			assertThat(dashboard().categorySpend()).isEmpty();
		}
	}

	@Nested
	@DisplayName("BR-12, what falls due next")
	class Upcoming {

		@Test
		@DisplayName("BR-12: sorts by date and states payments as money leaving")
		void sortsByDateAndSignsPaymentsNegative() {
			august();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa("386.40")));
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Credit union", new LoanTerms(of("2500.00"), 24, of("118.40"),
							Frequency.MONTHLY, 5),
					LocalDate.parse("2026-09-01"), REVOLUT)));

			List<DashboardResponse.UpcomingPaymentResponse> upcoming = dashboard().upcoming();

			// The loan on the 1st, then the card bill on the 5th.
			assertThat(upcoming).hasSize(2);
			assertThat(upcoming.get(0).date()).isEqualTo(LocalDate.parse("2026-09-01"));
			assertThat(upcoming.get(0).amount()).isEqualTo(-11840L);
			assertThat(upcoming.get(1).date()).isEqualTo(LocalDate.parse("2026-09-05"));
			assertThat(upcoming.get(1).amount()).isEqualTo(-38640L);
		}

		@Test
		@DisplayName("BR-12: a card with nothing on it is not a payment falling due")
		void aClearedCardIsNotDue() {
			august();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa("0.00")));

			assertThat(dashboard().upcoming()).isEmpty();
		}
	}

	@Test
	@DisplayName("BR-13: the accounts travel with the dashboard, cards named")
	void theAccountsTravelWithTheDashboard() {
		august();
		given(accounts.findAllForUser(ADA)).willReturn(List.of(revolut("842.30", true)));
		given(cardNames.cardNamesByAccount()).willReturn(Map.of(REVOLUT, List.of("Visa 4417")));

		assertThat(dashboard().accounts()).singleElement().satisfies(account -> {
			assertThat(account.balance()).isEqualTo(84230L);
			assertThat(account.cardNames()).containsExactly("Visa 4417");
		});
	}
}
