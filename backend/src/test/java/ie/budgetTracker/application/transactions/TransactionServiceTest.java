package ie.budgetTracker.application.transactions;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.financing.FinancingService;
import ie.budgetTracker.application.fx.FxService;
import ie.budgetTracker.application.plan.CategoryRepository;
import ie.budgetTracker.application.transactions.dto.CreateTransactionRequest;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Logging an entry: BR-8's conversion and BR-4's placement, with the ports
 * mocked.
 *
 * The rates are the prototype's own table, which is also what the frontend
 * fixtures serve, so a converted figure asserted here can be compared with the
 * screen it appears on.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID GROCERIES = UUID.randomUUID();
	private static final UUID TUTORING = UUID.randomUUID();
	private static final UUID VISA = UUID.randomUUID();
	private static final UUID REVOLUT = UUID.randomUUID();

	@Mock
	private TransactionRepository transactions;

	@Mock
	private CategoryRepository categories;

	@Mock
	private CardRepository cards;

	@Mock
	private AccountRepository accounts;

	@Mock
	private FxService fx;

	@Mock
	private FinancingService financing;

	private TransactionService service;

	@BeforeEach
	void setUp() {
		service = new TransactionService(transactions, categories, cards, accounts, fx, financing,
				() -> ADA);
	}

	/** What the user's totals are stated in. Needs no provider. */
	private void totalsAreInEuro() {
		given(fx.defaultCurrency()).willReturn(Currency.EUR);
	}

	private void ratesAreAvailable() {
		totalsAreInEuro();
		given(fx.currentRates()).willReturn(new ExchangeRates(Currency.EUR, Map.of(
				Currency.EUR, BigDecimal.ONE,
				Currency.USD, new BigDecimal("1.0858"),
				Currency.BRL, new BigDecimal("5.9134")),
				Instant.parse("2026-08-31T07:12:00Z")));
	}

	private void groceriesExists() {
		given(categories.findForUser(ADA, GROCERIES)).willReturn(Optional.of(new Category(
				GROCERIES, CategoryType.EXPENSE, "Groceries", "Variable", of("400.00"),
				Frequency.WEEKLY, LocalDate.parse("2026-01-03"), false)));
	}

	private void savedAsGiven() {
		given(transactions.create(eq(ADA), any())).willAnswer(call -> call.getArgument(1));
	}

	private void theVisaIsTheMethod() {
		given(cards.findForUser(ADA, VISA)).willReturn(Optional.of(new CreditCard(VISA,
				"Visa 4417", REVOLUT, of("2000.00"), of("386.40"), new StatementCycle(25, 5))));
	}

	private void theCurrentAccountIsTheMethod() {
		given(cards.findForUser(ADA, REVOLUT)).willReturn(Optional.empty());
		given(accounts.findForUser(ADA, REVOLUT)).willReturn(Optional.of(new Account(REVOLUT,
				"Revolut Current", AccountKind.BANK, of("842.30"), Currency.EUR, true, null,
				List.of())));
	}

	private static CreateTransactionRequest spend(long minorUnits, Currency currency, UUID method) {
		return new CreateTransactionRequest(TransactionType.EXPENSE, GROCERIES, minorUnits,
				currency, LocalDate.parse("2026-08-20"), method, null, null);
	}

	private Transaction written() {
		ArgumentCaptor<Transaction> written = ArgumentCaptor.forClass(Transaction.class);
		Mockito.verify(transactions).create(eq(ADA), written.capture());
		return written.getValue();
	}

	@Nested
	@DisplayName("BR-8, the currency it was typed in")
	class Conversion {

		@Test
		@DisplayName("BR-8: keeps the amount as typed, the converted amount and the rate")
		void keepsAllThreeFigures() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			ratesAreAvailable();
			savedAsGiven();

			service.log(spend(10000L, Currency.USD, REVOLUT));

			// 100 USD at 1.0858 per EUR is 92.10 EUR. All three are stored, because
			// any two without the third is a figure nobody can explain later.
			Transaction entry = written();
			assertThat(entry.amount()).isEqualByComparingTo(of("100.00"));
			assertThat(entry.currency()).isEqualTo(Currency.USD);
			assertThat(entry.amountInDefaultCurrency()).isEqualByComparingTo(of("92.10"));
			assertThat(entry.fxRate()).isNotNull();
		}

		@Test
		@DisplayName("BR-8: a same-currency entry records a rate of exactly one")
		void aSameCurrencyEntryRecordsARateOfOne() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(8000L, Currency.EUR, REVOLUT));

			Transaction entry = written();
			assertThat(entry.fxRate()).isEqualByComparingTo(BigDecimal.ONE);
			assertThat(entry.amountInDefaultCurrency()).isEqualByComparingTo(of("80.00"));
		}

		@Test
		@DisplayName("BR-8: an entry needing no conversion never asks the rate provider")
		void anEntryNeedingNoConversionNeverAsksTheProvider() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(8000L, Currency.EUR, REVOLUT));

			// BR-8 forbids guessing a rate, and one is not a guess when nothing is
			// converted. Asking anyway would mean an outage at a third party
			// stopped somebody logging a euro expense from a euro account.
			Mockito.verify(fx, Mockito.never()).currentRates();
		}

		@Test
		@DisplayName("BR-8: a currency with no rate blocks the save rather than guessing")
		void aMissingRateBlocksTheSave() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			given(fx.currentRates()).willReturn(new ExchangeRates(Currency.EUR,
					Map.of(Currency.EUR, BigDecimal.ONE), Instant.parse("2026-08-31T07:12:00Z")));

			// Nothing is written. A guessed rate would produce a total that is
			// wrong in a way nobody can see.
			assertThatThrownBy(() -> service.log(spend(10000L, Currency.BRL, REVOLUT)))
					.isInstanceOf(AppException.class)
					.satisfies(refused -> assertThat(((AppException) refused).kind())
							.isEqualTo(AppException.Kind.UNAVAILABLE))
					.hasMessageContaining("has not been saved");

			Mockito.verify(transactions, Mockito.never()).create(any(), any());
		}
	}

	@Nested
	@DisplayName("BR-4 and BR-5, when the money is actually owed")
	class Placement {

		@Test
		@DisplayName("BR-4: a credit-card expense is planned for its bill date")
		void aCreditCardExpenseIsPlannedForItsBillDate() {
			groceriesExists();
			theVisaIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(5000L, Currency.EUR, VISA));

			// Bought on the 20th, closing on the 25th, due on the 5th: the money is
			// owed on 5 September, not on the day it was spent.
			Transaction entry = written();
			assertThat(entry.plannedExpenseDate()).isEqualTo(LocalDate.parse("2026-09-05"));
			assertThat(entry.paymentMethod().kind()).isEqualTo(PaymentMethodKind.CREDIT_CARD);
		}

		@Test
		@DisplayName("BR-5: a debit-card expense has no bill date at all")
		void aDebitCardExpenseHasNoBillDate() {
			groceriesExists();
			given(cards.findForUser(ADA, VISA)).willReturn(
					Optional.of(new DebitCard(VISA, "Revolut debit", REVOLUT)));
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(5000L, Currency.EUR, VISA));

			// The money left the account the same day, so a planned date would put
			// it on a bill that will never carry it.
			Transaction entry = written();
			assertThat(entry.plannedExpenseDate()).isNull();
			assertThat(entry.paymentMethod().kind()).isEqualTo(PaymentMethodKind.DEBIT_CARD);
		}

		@Test
		@DisplayName("BR-4: an earning paid onto a credit card is not deferred")
		void anEarningOntoACardIsNotDeferred() {
			given(categories.findForUser(ADA, TUTORING)).willReturn(Optional.of(new Category(
					TUTORING, CategoryType.EARNING, "Tutoring", "Self-employed", of("160.00"),
					Frequency.WEEKLY, LocalDate.parse("2026-01-05"), false)));
			theVisaIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(new CreateTransactionRequest(TransactionType.EARNING, TUTORING, 16000L,
					Currency.EUR, LocalDate.parse("2026-08-20"), VISA, null, null));

			// BR-4 is about when spending is billed. Money arriving is not billed.
			assertThat(written().plannedExpenseDate()).isNull();
		}

		@Test
		void anAccountExpenseHasNoBillDateEither() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(5000L, Currency.EUR, REVOLUT));

			assertThat(written().plannedExpenseDate()).isNull();
			assertThat(written().paymentMethod().kind()).isEqualTo(PaymentMethodKind.ACCOUNT);
		}
	}

	@Nested
	@DisplayName("what an entry may be logged against")
	class Validation {

		@Test
		@DisplayName("BR-9: an earning cannot be logged against an expense category")
		void anEarningCannotGoIntoAnExpenseCategory() {
			groceriesExists();

			// Otherwise the variance would be drawn against a plan that means the
			// opposite of the figure, and the colour would call good news bad.
			assertThatThrownBy(() -> service.log(new CreateTransactionRequest(
					TransactionType.EARNING, GROCERIES, 1000L, Currency.EUR,
					LocalDate.parse("2026-08-20"), REVOLUT, null, null)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("categoryId");
		}

		@Test
		@DisplayName("a saving belongs to the expense side, because the money left the plan")
		void aSavingGoesIntoAnExpenseCategory() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(new CreateTransactionRequest(TransactionType.SAVING, GROCERIES, 1000L,
					Currency.EUR, LocalDate.parse("2026-08-20"), REVOLUT, null, null));

			assertThat(written().type()).isEqualTo(TransactionType.SAVING);
		}

		@Test
		void answersNotFoundForACategoryThisUserDoesNotHave() {
			given(categories.findForUser(ADA, GROCERIES)).willReturn(Optional.empty());

			// ADR-11: a 403 would confirm the id exists.
			assertThatThrownBy(() -> service.log(spend(1000L, Currency.EUR, REVOLUT)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.NOT_FOUND);
		}

		@Test
		@DisplayName("an unknown payment method answers the same as somebody else's")
		void anUnknownPaymentMethodIsNotFound() {
			groceriesExists();
			given(cards.findForUser(ADA, REVOLUT)).willReturn(Optional.empty());
			given(accounts.findForUser(ADA, REVOLUT)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.log(spend(1000L, Currency.EUR, REVOLUT)))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("No account or card");
		}

		@Test
		@DisplayName("BR-6: a financed purchase is written with the plan that spreads it")
		void aFinancedPurchaseCarriesItsPlan() {
			UUID planId = UUID.randomUUID();
			groceriesExists();
			theVisaIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();
			given(financing.planFor(eq(VISA), any(), eq(39900L), eq(6), eq(7150L),
					eq(Frequency.MONTHLY), any()))
					.willReturn(new InstalmentPlan(planId, VISA, "Groceries",
							new InstalmentTerms(of("399.00"), 6, of("71.50"), Frequency.MONTHLY),
							0, LocalDate.parse("2026-09-05")));

			service.log(new CreateTransactionRequest(TransactionType.EXPENSE, GROCERIES, 39900L,
					Currency.EUR, LocalDate.parse("2026-08-20"), VISA, null,
					new CreateTransactionRequest.Financing(6, 7150L, Frequency.MONTHLY)));

			// Spec §4 asks for the plan to be created atomically with the purchase.
			// An entry saved without it would sit in the ledger as an ordinary
			// expense, and BR-3's derived row would be short by what is owed.
			assertThat(written().instalmentPlanId()).isEqualTo(planId);
		}

		@Test
		@DisplayName("an ordinary purchase creates no plan at all")
		void anOrdinaryPurchaseCreatesNoPlan() {
			groceriesExists();
			theCurrentAccountIsTheMethod();
			totalsAreInEuro();
			savedAsGiven();

			service.log(spend(5000L, Currency.EUR, REVOLUT));

			assertThat(written().instalmentPlanId()).isNull();
			Mockito.verifyNoInteractions(financing);
		}
	}
}
