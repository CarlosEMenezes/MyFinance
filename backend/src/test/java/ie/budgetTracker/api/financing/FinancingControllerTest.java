package ie.budgetTracker.api.financing;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.financing.FinancingService;
import ie.budgetTracker.application.financing.dto.CreateLoanRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPlanResponse;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewRequest;
import ie.budgetTracker.application.financing.dto.LoanPreviewRequest;
import ie.budgetTracker.application.financing.dto.LoanResponse;
import ie.budgetTracker.domain.financing.InstalmentCalculator;
import ie.budgetTracker.domain.financing.InstalmentPlan;
import ie.budgetTracker.domain.financing.InstalmentTerms;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanCalculator;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of `/instalment-plans` and `/loans`, against the frozen
 * contract (ADR-12), including the two preview endpoints spec §4 names.
 */
@WebMvcTest(FinancingController.class)
class FinancingControllerTest extends ie.budgetTracker.api.WebSliceTest {

	private static final UUID VISA = UUID.fromString("88888888-8888-4888-8888-888888888888");
	private static final UUID REVOLUT = UUID.fromString("99999999-9999-4999-8999-999999999999");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private FinancingService financing;

	private static InstalmentPlanResponse laptop() {
		InstalmentTerms terms =
				new InstalmentTerms(of("399.00"), 6, of("71.50"), Frequency.MONTHLY);

		return InstalmentPlanResponse.from(
				new InstalmentPlan(UUID.randomUUID(), VISA, "Laptop", terms, 0,
						LocalDate.parse("2026-09-05")),
				InstalmentCalculator.analyse(terms));
	}

	private static LoanResponse creditUnion() {
		LoanTerms terms = new LoanTerms(of("2500.00"), 24, of("118.40"), Frequency.MONTHLY, 5);

		return LoanResponse.from(
				new Loan(UUID.randomUUID(), "Credit union", terms, LocalDate.parse("2026-09-01"),
						REVOLUT),
				LoanCalculator.analyse(terms));
	}

	@Test
	@DisplayName("BR-6: a plan states money in minor units and the rates as fractions")
	void aPlanStatesItsFigures() throws Exception {
		given(financing.plans()).willReturn(List.of(laptop()));

		mvc.perform(get("/api/v1/instalment-plans").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cashPrice").value(39900))
				.andExpect(jsonPath("$[0].instalmentAmount").value(7150))
				.andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
				.andExpect(jsonPath("$[0].firstDueDate").value("2026-09-05"))
				.andExpect(jsonPath("$[0].interest.financedTotal").value(42900))
				.andExpect(jsonPath("$[0].interest.interest").value(3000))
				// Fractions, not percentages: the screen decides how to print it.
				.andExpect(jsonPath("$[0].interest.annualRate").isNumber())
				.andExpect(jsonPath("$[0].interest.aboveDisplayCap").value(false));
	}

	@Test
	@DisplayName("BR-7: a loan states the settlement figure and the saving as fields")
	void aLoanStatesTheSettlementAndSaving() throws Exception {
		given(financing.loans()).willReturn(List.of(creditUnion()));

		mvc.perform(get("/api/v1/loans").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].principal").value(250000))
				.andExpect(jsonPath("$[0].instalmentsRemaining").value(19))
				.andExpect(jsonPath("$[0].remainingRepayable").value(224960))
				.andExpect(jsonPath("$[0].settlementFigureToday").value(202959))
				.andExpect(jsonPath("$[0].earlyPayoffSaving").value(22001));
	}

	@Test
	@DisplayName("BR-2: creating a loan answers 201 with both sides already computed")
	void creatingALoanAnswers201() throws Exception {
		given(financing.createLoan(any(CreateLoanRequest.class))).willReturn(creditUnion());

		mvc.perform(post("/api/v1/loans")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"label":"Credit union","principal":250000,"instalmentCount":24,
						 "instalmentAmount":11840,"frequency":"MONTHLY","instalmentsPaid":5,
						 "firstDueDate":"2026-09-01","depositAccountId":"%s"}"""
						.formatted(REVOLUT)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.depositAccountId").value(REVOLUT.toString()));
	}

	@Test
	@DisplayName("BR-2: a loan with nowhere to land is refused, naming the field")
	void aLoanNeedsADepositAccount() throws Exception {
		// BR-2 raises what is available by the principal, and it has to be raised
		// somewhere in particular.
		mvc.perform(post("/api/v1/loans")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"label":"Credit union","principal":250000,"instalmentCount":24,
						 "instalmentAmount":11840,"frequency":"MONTHLY",
						 "firstDueDate":"2026-09-01"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("depositAccountId"));
	}

	@Test
	@DisplayName("a preview writes nothing and answers 200, not 201")
	void aPreviewAnswers200() throws Exception {
		given(financing.previewLoan(any(LoanPreviewRequest.class))).willReturn(
				new ie.budgetTracker.application.financing.dto.LoanPreviewResponse(
						creditUnion().interest(), 250000L, 284160L, 202959L));

		// 201 would say something was created, and nothing was.
		mvc.perform(post("/api/v1/loans/preview")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"principal":250000,"instalmentCount":24,"instalmentAmount":11840,
						 "frequency":"MONTHLY"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.addsToAvailable").value(250000))
				.andExpect(jsonPath("$.addsToOwed").value(284160));
	}

	@Test
	@DisplayName("BR-5: previewing instalments on a debit card is a 400 that names the card")
	void previewingOnADebitCardIsRefused() throws Exception {
		willThrow(AppException.invalid("cardId", "\"Revolut debit\" is a debit card"))
				.given(financing).previewInstalments(any(InstalmentPreviewRequest.class));

		mvc.perform(post("/api/v1/instalment-plans/preview")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"cashPrice":39900,"instalmentCount":6,"instalmentAmount":7150,
						 "frequency":"MONTHLY","cardId":"%s","purchaseDate":"2026-08-20"}"""
						.formatted(VISA)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("cardId"));
	}

	@Test
	void refusesToAnswerWithoutASession() throws Exception {
		mvc.perform(get("/api/v1/loans")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/instalment-plans")).andExpect(status().isUnauthorized());
	}
}
