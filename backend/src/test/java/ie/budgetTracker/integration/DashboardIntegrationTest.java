package ie.budgetTracker.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import ie.budgetTracker.application.fx.ExchangeRateProvider;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

/**
 * The whole overview, from the HTTP request to PostgreSQL and back
 * (spec §6 step 8).
 *
 * August 2026 throughout: the month the prototype was drawn against, and the
 * one that holds five weekly landings from an anchor early in January. That is
 * the difference BR-10 exists to get right.
 */
@Import(DashboardIntegrationTest.FixedRates.class)
class DashboardIntegrationTest extends IntegrationTest {

	@TestConfiguration
	static class FixedRates {
		@Bean
		@Primary
		ExchangeRateProvider rates() {
			return base -> new ExchangeRates(base,
					Map.of(Currency.EUR, BigDecimal.ONE, Currency.USD, new BigDecimal("1.0858")),
					Instant.parse("2026-08-31T07:12:00Z"));
		}
	}

	private String accountFor(Cookie session, String balance) throws Exception {
		return idOf(mvc().perform(post("/api/v1/accounts")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Revolut Current","kind":"BANK","balance":%s,"currency":"EUR",
						 "includeInTotals":true}""".formatted(balance)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private String categoryFor(Cookie session, String name, String type, String group,
			long planned, String frequency, String anchor) throws Exception {
		return idOf(mvc().perform(post("/api/v1/categories")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","type":"%s","group":"%s","plannedAmount":%d,
						 "plannedFrequency":"%s","anchorDate":"%s"}"""
						.formatted(name, type, group, planned, frequency, anchor)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private void log(Cookie session, String type, String category, long amount, String date,
			String method) throws Exception {
		mvc().perform(post("/api/v1/transactions")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"%s","categoryId":"%s","amount":%d,"currency":"EUR",
						 "date":"%s","paymentMethodId":"%s"}"""
						.formatted(type, category, amount, date, method)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("BR-10, BR-9: one call answers the plan, the real and the variance")
	void oneCallAnswersThePlanTheRealAndTheVariance() throws Exception {
		Cookie ada = register("ada-dash@example.com");
		String account = accountFor(ada, "50000");
		String groceries = categoryFor(ada, "Groceries", "EXPENSE", "Variable", 10000,
				"WEEKLY", "2026-01-03");

		log(ada, "EXPENSE", groceries, 60000, "2026-08-05", account);

		mvc().perform(get("/api/v1/dashboard?period=MONTH").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period.label").value("August 2026"))
				// 100.00 weekly lands five times in August, so the plan is 500.00.
				.andExpect(jsonPath("$.expenses[0].occurrencesInPeriod").value(5))
				.andExpect(jsonPath("$.expenses[0].planned").value(50000))
				.andExpect(jsonPath("$.expenses[0].real").value(60000))
				// real - planned, and overspending an expense is bad news.
				.andExpect(jsonPath("$.expenses[0].variance").value(10000))
				.andExpect(jsonPath("$.expenses[0].varianceTone").value("BAD"))
				.andExpect(jsonPath("$.totals.expensesReal").value(60000));
	}

	@Test
	@DisplayName("BR-1: what was earned is added and what left an account is taken off")
	void thePositionReflectsWhatWasLogged() throws Exception {
		Cookie ada = register("ada-position@example.com");
		String account = accountFor(ada, "50000");
		String wages = categoryFor(ada, "Wages", "EARNING", "Employment", 100000, "MONTHLY",
				"2026-01-01");
		String groceries = categoryFor(ada, "Groceries", "EXPENSE", "Variable", 10000, "WEEKLY",
				"2026-01-03");

		log(ada, "EARNING", wages, 100000, "2026-08-01", account);
		log(ada, "EXPENSE", groceries, 20000, "2026-08-05", account);

		// 500.00 opening, plus 1,000.00 earned, less 200.00 spent.
		mvc().perform(get("/api/v1/dashboard").cookie(ada))
				.andExpect(jsonPath("$.position.availableNow").value(130000))
				.andExpect(jsonPath("$.position.owed").value(0))
				.andExpect(jsonPath("$.position.totalMoneyNow").value(130000));
	}

	@Test
	@DisplayName("BR-4, BR-1: a card purchase lands on its bill month and is owed, not spent")
	void aCardPurchaseLandsOnItsBillMonth() throws Exception {
		Cookie ada = register("ada-cardmonth@example.com");
		String account = accountFor(ada, "50000");
		String card = idOf(mvc().perform(post("/api/v1/cards")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"CREDIT","name":"Visa 4417","accountId":"%s","creditLimit":200000,
						 "closingDay":25,"dueDay":5}""".formatted(account)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		String groceries = categoryFor(ada, "Groceries", "EXPENSE", "Variable", 10000, "WEEKLY",
				"2026-01-03");

		// Bought on 20 August, billed on 5 September (BR-4).
		log(ada, "EXPENSE", groceries, 5000, "2026-08-20", card);

		// August sees the plan but not the spend: the money is not owed yet.
		mvc().perform(get("/api/v1/dashboard?period=MONTH").cookie(ada))
				.andExpect(jsonPath("$.expenses[0].real").value(0))
				// And what is available is untouched, because nothing left the
				// account (BR-1).
				.andExpect(jsonPath("$.position.availableNow").value(50000));

		// September, where the bill actually falls.
		mvc().perform(get("/api/v1/dashboard?period=CUSTOM&from=2026-09-01&to=2026-09-30")
				.cookie(ada))
				.andExpect(jsonPath("$.expenses[0].real").value(5000));
	}

	@Test
	@DisplayName("BR-3: a loan shows as a read-only row and moves both sides of the position")
	void aLoanShowsAsAReadOnlyRow() throws Exception {
		Cookie ada = register("ada-dashloan@example.com");
		String account = accountFor(ada, "0");

		mvc().perform(post("/api/v1/loans")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"label":"Credit union","principal":250000,"instalmentCount":24,
						 "instalmentAmount":11840,"frequency":"MONTHLY",
						 "firstDueDate":"2026-09-01","depositAccountId":"%s"}"""
						.formatted(account)))
				.andExpect(status().isCreated());

		mvc().perform(get("/api/v1/dashboard").cookie(ada))
				// BR-3: 118.40 monthly, averaged at 52/12 for a monthly plan is
				// itself, and BR-14 renders it as text.
				.andExpect(jsonPath("$.expenses[0].category").value("Loan repayments"))
				.andExpect(jsonPath("$.expenses[0].derived").value(true))
				.andExpect(jsonPath("$.expenses[0].planned").value(11840))
				// BR-2: both sides move, and the difference is the interest.
				.andExpect(jsonPath("$.position.borrowed").value(250000))
				.andExpect(jsonPath("$.position.owedOnLoans").value(284160))
				.andExpect(jsonPath("$.position.totalMoneyNow").value(-34160));
	}

	@Test
	@DisplayName("another user sees their own dashboard and nothing of anyone else's")
	void anotherUserSeesTheirOwnDashboard() throws Exception {
		Cookie ada = register("ada-dashiso@example.com");
		Cookie grace = register("grace-dashiso@example.com");

		String adasAccount = accountFor(ada, "50000");
		String adasGroceries = categoryFor(ada, "Groceries", "EXPENSE", "Variable", 10000,
				"WEEKLY", "2026-01-03");
		log(ada, "EXPENSE", adasGroceries, 60000, "2026-08-05", adasAccount);

		mvc().perform(get("/api/v1/dashboard").cookie(grace))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expenses").isEmpty())
				.andExpect(jsonPath("$.accounts").isEmpty())
				.andExpect(jsonPath("$.position.availableNow").value(0));
	}
}
