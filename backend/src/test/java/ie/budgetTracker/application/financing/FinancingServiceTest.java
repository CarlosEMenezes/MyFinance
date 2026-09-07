package ie.budgetTracker.application.financing;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.financing.dto.CreateLoanRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewResponse;
import ie.budgetTracker.application.financing.dto.LoanPreviewRequest;
import ie.budgetTracker.application.financing.dto.LoanPreviewResponse;
import ie.budgetTracker.application.financing.dto.LoanResponse;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BR-6, BR-7 and BR-2 as the API states them, with the ports mocked.
 *
 * Every figure asserted here is a row of docs/business-rule-vectors.md. The
 * calculators themselves have their own tests; what this covers is that the
 * right figures reach the wire, in minor units, and that nothing is stored
 * which should be solved.
 */
@ExtendWith(MockitoExtension.class)
class FinancingServiceTest {

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID VISA = UUID.randomUUID();
	private static final UUID REVOLUT = UUID.randomUUID();

	@Mock
	private FinancingRepository financing;

	@Mock
	private CardRepository cards;

	private FinancingService service;

	@BeforeEach
	void setUp() {
		service = new FinancingService(financing, cards, () -> ADA);
	}

	private void theVisaExists() {
		given(cards.findForUser(ADA, VISA)).willReturn(Optional.of(new CreditCard(VISA,
				"Visa 4417", REVOLUT, of("2000.00"), of("386.40"), new StatementCycle(25, 5))));
	}

	/** The prototype's laptop: €399.00 spread over six of €71.50. */
	private static InstalmentPlan laptop() {
		return new InstalmentPlan(UUID.randomUUID(), VISA, "Laptop",
				new InstalmentTerms(of("399.00"), 6, of("71.50"), Frequency.MONTHLY), 0,
				LocalDate.parse("2026-09-05"));
	}

	/** The prototype's credit-union loan, five of twenty-four paid. */
	private static Loan creditUnion() {
		return new Loan(UUID.randomUUID(), "Credit union",
				new LoanTerms(of("2500.00"), 24, of("118.40"), Frequency.MONTHLY, 5),
				LocalDate.parse("2026-09-01"), REVOLUT);
	}

	@Nested
	@DisplayName("BR-6, a purchase spread over instalments")
	class Plans {

		@Test
		@DisplayName("BR-6: answers the interest worked out, not the ingredients for it")
		void answersTheInterestWorkedOut() {
			given(financing.findPlansForUser(ADA)).willReturn(List.of(laptop()));

			var plan = service.plans().get(0);

			// 6 x 71.50 finances 429.00 against a cash price of 399.00.
			assertThat(plan.interest().financedTotal()).isEqualTo(42900L);
			assertThat(plan.interest().interest()).isEqualTo(3000L);
			assertThat(plan.interest().interestFree()).isFalse();
			assertThat(plan.cashPrice()).isEqualTo(39900L);
		}

		@Test
		@DisplayName("BR-6, BR-4: a preview states the cost and the bill it first lands on")
		void previewStatesTheCostAndTheFirstBill() {
			theVisaExists();

			InstalmentPreviewResponse preview = service.previewInstalments(
					new InstalmentPreviewRequest(39900L, 6, 7150L, Frequency.MONTHLY, VISA,
							LocalDate.parse("2026-08-20")));

			assertThat(preview.interest().interest()).isEqualTo(3000L);
			// Bought on the 20th, closing the 25th, due the 5th (BR-4).
			assertThat(preview.firstDueDate()).isEqualTo(LocalDate.parse("2026-09-05"));
		}

		@Test
		@DisplayName("BR-6: an interest-free plan says so rather than reporting a tiny rate")
		void anInterestFreePlanSaysSo() {
			theVisaExists();

			// 6 x 100.00 against a cash price of 600.00: nothing is being charged.
			InstalmentPreviewResponse preview = service.previewInstalments(
					new InstalmentPreviewRequest(60000L, 6, 10000L, Frequency.MONTHLY, VISA,
							LocalDate.parse("2026-08-20")));

			assertThat(preview.interest().interestFree()).isTrue();
			assertThat(preview.interest().interest()).isZero();
		}

		@Test
		@DisplayName("BR-5: a debit card cannot carry instalments, and the message says why")
		void aDebitCardCannotCarryInstalments() {
			given(cards.findForUser(ADA, VISA))
					.willReturn(Optional.of(new DebitCard(VISA, "Revolut debit", REVOLUT)));

			assertThatThrownBy(() -> service.previewInstalments(new InstalmentPreviewRequest(
					39900L, 6, 7150L, Frequency.MONTHLY, VISA, LocalDate.parse("2026-08-20"))))
					.isInstanceOf(AppException.class)
					.satisfies(refused -> assertThat(((AppException) refused).field())
							.isEqualTo("cardId"))
					.hasMessageContaining("same day");
		}

		@Test
		void anUnknownCardIsNotFound() {
			given(cards.findForUser(ADA, VISA)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.previewInstalments(new InstalmentPreviewRequest(
					39900L, 6, 7150L, Frequency.MONTHLY, VISA, LocalDate.parse("2026-08-20"))))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.NOT_FOUND);
		}

		@Test
		@DisplayName("BR-4: a plan written with a purchase lands on the bill, not the day")
		void aPlanWrittenWithAPurchaseLandsOnTheBill() {
			theVisaExists();
			given(financing.createPlan(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			InstalmentPlan plan = service.planFor(VISA, "Laptop", 39900L, 6, 7150L,
					Frequency.MONTHLY, LocalDate.parse("2026-08-20"));

			assertThat(plan.firstDueDate()).isEqualTo(LocalDate.parse("2026-09-05"));
			assertThat(plan.instalmentsPaid()).isZero();
		}
	}

	@Nested
	@DisplayName("BR-7 and BR-2, borrowing")
	class Loans {

		@Test
		@DisplayName("BR-7: states the settlement figure and what settling now saves")
		void statesTheSettlementFigureAndTheSaving() {
			given(financing.findLoansForUser(ADA)).willReturn(List.of(creditUnion()));

			LoanResponse loan = service.loans().get(0);

			// docs/business-rule-vectors.md, to the cent.
			assertThat(loan.instalmentsRemaining()).isEqualTo(19);
			assertThat(loan.remainingRepayable()).isEqualTo(224960L);
			assertThat(loan.settlementFigureToday()).isEqualTo(202959L);
			assertThat(loan.earlyPayoffSaving()).isEqualTo(22001L);
		}

		@Test
		@DisplayName("BR-2: a preview shows both sides, so borrowing never looks like income")
		void aPreviewShowsBothSides() {
			LoanPreviewResponse preview = service.previewLoan(
					new LoanPreviewRequest(250000L, 24, 11840L, Frequency.MONTHLY));

			// The principal on one side, the whole repayment on the other. The net
			// effect on the position is exactly the interest, and nothing else.
			assertThat(preview.addsToAvailable()).isEqualTo(250000L);
			assertThat(preview.addsToOwed()).isEqualTo(284160L);
			assertThat(preview.addsToOwed() - preview.addsToAvailable())
					.isEqualTo(preview.interest().interest());
		}

		@Test
		@DisplayName("BR-7: an interest-free loan saves nothing by being settled early")
		void anInterestFreeLoanSavesNothing() {
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Family", new LoanTerms(of("600.00"), 6, of("100.00"), Frequency.MONTHLY, 3),
					LocalDate.parse("2026-09-01"), REVOLUT)));

			LoanResponse loan = service.loans().get(0);

			// Settling early gains nothing, and the payload must not imply it does.
			assertThat(loan.interest().interestFree()).isTrue();
			assertThat(loan.remainingRepayable()).isEqualTo(30000L);
			assertThat(loan.settlementFigureToday()).isEqualTo(30000L);
			assertThat(loan.earlyPayoffSaving()).isZero();
		}

		@Test
		@DisplayName("BR-2: records principal, terms and deposit account in one write")
		void recordsEverythingInOneWrite() {
			given(financing.createLoan(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			LoanResponse loan = service.createLoan(new CreateLoanRequest("Credit union", 250000L,
					24, 11840L, Frequency.MONTHLY, LocalDate.parse("2026-09-01"), REVOLUT, 5));

			assertThat(loan.principal()).isEqualTo(250000L);
			assertThat(loan.depositAccountId()).isEqualTo(REVOLUT);
			assertThat(loan.instalmentsPaid()).isEqualTo(5);
		}

		@Test
		@DisplayName("a loan with no instalments paid defaults to none, not to null")
		void instalmentsPaidDefaultsToNone() {
			given(financing.createLoan(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			LoanResponse loan = service.createLoan(new CreateLoanRequest("Credit union", 250000L,
					24, 11840L, Frequency.MONTHLY, LocalDate.parse("2026-09-01"), REVOLUT, null));

			assertThat(loan.instalmentsPaid()).isZero();
			assertThat(loan.instalmentsRemaining()).isEqualTo(24);
		}
	}
}
