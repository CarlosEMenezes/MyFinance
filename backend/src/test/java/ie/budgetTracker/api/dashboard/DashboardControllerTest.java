package ie.budgetTracker.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.dashboard.DashboardService;
import ie.budgetTracker.application.dashboard.dto.DashboardResponse;
import ie.budgetTracker.application.dashboard.dto.PlanRowResponse;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.PeriodKind;
import ie.budgetTracker.domain.plan.VarianceTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of `GET /dashboard`, against the frozen contract (ADR-12).
 *
 * Three pages read this one payload, so every figure they draw has to be a
 * field on it. A screen that had to add two of these up would be recomputing a
 * business figure, which spec §4 forbids.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest extends ie.budgetTracker.api.WebSliceTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DashboardService dashboard;

	private static DashboardResponse august() {
		return new DashboardResponse(
				new PeriodWindowResponse(PeriodKind.MONTH, LocalDate.parse("2026-08-01"),
						LocalDate.parse("2026-08-31"), "August 2026"),
				new DashboardResponse.PositionResponse(-135558L, 241230L, 376788L, 38640L,
						83188L, 254960L, 250000L),
				List.of(new PlanRowResponse("tutoring", "Tutoring", CategoryType.EARNING,
						"Self-employed", 80000L, 67200L, -12800L, VarianceTone.BAD, 16000L,
						Frequency.WEEKLY, 5, 69333L, false, null, null, null)),
				List.of(new PlanRowResponse("loan-repayments", "Loan repayments",
						CategoryType.EXPENSE, "Debt", 11840L, 11840L, 0L, VarianceTone.NEUTRAL,
						11840L, Frequency.MONTHLY, 1, 11840L, true, "Revolut Current", null,
						null)),
				new DashboardResponse.Totals(80000L, 67200L, 11840L, 11840L, 68160L, 55360L),
				List.of(new DashboardResponse.UpcomingPaymentResponse("visa-bill",
						"Visa card payment", "statement closes day 25",
						LocalDate.parse("2026-09-05"), -38640L)),
				List.of(new DashboardResponse.CategorySpendResponse("rent", "Rent", 78000L,
						78000L, 100, 100)),
				List.of());
	}

	@Test
	@DisplayName("BR-1: answers the whole position, computed, in minor units")
	void answersTheWholePosition() throws Exception {
		given(dashboard.forPeriod(eq("MONTH"), any(), any())).willReturn(august());

		mvc.perform(get("/api/v1/dashboard?period=MONTH").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period.label").value("August 2026"))
				// Negative, and not floored: it is the figure the app exists to show
				// honestly.
				.andExpect(jsonPath("$.position.totalMoneyNow").value(-135558))
				.andExpect(jsonPath("$.position.availableNow").value(241230))
				.andExpect(jsonPath("$.position.owedOnCards").value(38640))
				.andExpect(jsonPath("$.position.borrowed").value(250000));
	}

	@Test
	@DisplayName("BR-9, BR-14: a row carries its variance, its tone and whether it is derived")
	void aRowCarriesEverythingAScreenNeeds() throws Exception {
		given(dashboard.forPeriod(eq("MONTH"), any(), any())).willReturn(august());

		mvc.perform(get("/api/v1/dashboard").cookie(session()))
				.andExpect(jsonPath("$.earnings[0].variance").value(-12800))
				.andExpect(jsonPath("$.earnings[0].varianceTone").value("BAD"))
				.andExpect(jsonPath("$.earnings[0].occurrencesInPeriod").value(5))
				.andExpect(jsonPath("$.earnings[0].monthlyEquivalent").value(69333))
				.andExpect(jsonPath("$.earnings[0].derived").value(false))
				// BR-14: this is what tells the table to draw text, not an input.
				.andExpect(jsonPath("$.expenses[0].derived").value(true))
				.andExpect(jsonPath("$.expenses[0].paidWith").value("Revolut Current"));
	}

	@Test
	@DisplayName("BR-15: the totals are on the payload, not summed from the rows")
	void theTotalsAreOnThePayload() throws Exception {
		given(dashboard.forPeriod(eq("MONTH"), any(), any())).willReturn(august());

		// A screen that filters its rows still shows these, which is what stops
		// "total spent" meaning two different things on two pages.
		mvc.perform(get("/api/v1/dashboard").cookie(session()))
				.andExpect(jsonPath("$.totals.earningsPlanned").value(80000))
				.andExpect(jsonPath("$.totals.netReal").value(55360));
	}

	@Test
	@DisplayName("BR-12: upcoming payments are dated and signed")
	void upcomingPaymentsAreDatedAndSigned() throws Exception {
		given(dashboard.forPeriod(eq("MONTH"), any(), any())).willReturn(august());

		mvc.perform(get("/api/v1/dashboard").cookie(session()))
				.andExpect(jsonPath("$.upcoming[0].date").value("2026-09-05"))
				.andExpect(jsonPath("$.upcoming[0].amount").value(-38640))
				.andExpect(jsonPath("$.categorySpend[0].percentOfLargest").value(100));
	}

	@Test
	void passesACustomWindowStraightThrough() throws Exception {
		given(dashboard.forPeriod(eq("CUSTOM"), eq(LocalDate.parse("2026-08-10")),
				eq(LocalDate.parse("2026-08-20")))).willReturn(august());

		mvc.perform(get("/api/v1/dashboard?period=CUSTOM&from=2026-08-10&to=2026-08-20")
				.cookie(session()))
				.andExpect(status().isOk());
	}

	@Test
	void answersABadPeriodWithASentenceNamingTheParameter() throws Exception {
		willThrow(AppException.invalid("period", "Ask for one of DAY, WEEK, MONTH, YEAR or CUSTOM"))
				.given(dashboard).forPeriod(any(), any(), any());

		mvc.perform(get("/api/v1/dashboard?period=FORTNIGHT").cookie(session()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("period"));
	}

	@Test
	void refusesToAnswerWithoutASession() throws Exception {
		mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
	}
}
