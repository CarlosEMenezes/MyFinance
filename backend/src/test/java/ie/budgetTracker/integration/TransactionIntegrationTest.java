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
 * Logging an entry, from the HTTP request to PostgreSQL and back
 * (spec §6 step 6).
 *
 * The rate provider is the one thing stubbed, because reaching a third party
 * over the network from a test would make the build fail for reasons that have
 * nothing to do with this code. The real adapter has its own test, and the
 * table used here is the prototype's own.
 */
@Import(TransactionIntegrationTest.FixedRates.class)
class TransactionIntegrationTest extends IntegrationTest {

	@TestConfiguration
	static class FixedRates {
		@Bean
		@Primary
		ExchangeRateProvider rates() {
			return base -> new ExchangeRates(base, Map.of(
					Currency.EUR, BigDecimal.ONE,
					Currency.USD, new BigDecimal("1.0858"),
					Currency.GBP, new BigDecimal("0.8422"),
					Currency.BRL, new BigDecimal("5.9134")),
					Instant.parse("2026-08-31T07:12:00Z"));
		}
	}

	private String accountFor(Cookie session) throws Exception {
		return idOf(mvc().perform(post("/api/v1/accounts")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Revolut Current","kind":"BANK","balance":84230,"currency":"EUR",
						 "includeInTotals":true}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private String creditCardFor(Cookie session, String accountId) throws Exception {
		return idOf(mvc().perform(post("/api/v1/cards")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"CREDIT","name":"Visa 4417","accountId":"%s","creditLimit":200000,
						 "closingDay":25,"dueDay":5}""".formatted(accountId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private String groceriesFor(Cookie session) throws Exception {
		return idOf(mvc().perform(post("/api/v1/categories")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Groceries","type":"EXPENSE","group":"Variable","plannedAmount":40000,
						 "plannedFrequency":"WEEKLY","anchorDate":"2026-01-03"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	@DisplayName("BR-8, BR-4: a foreign card purchase is converted and placed on its bill")
	void aForeignCardPurchaseIsConvertedAndPlaced() throws Exception {
		Cookie ada = register("ada-log@example.com");
		String card = creditCardFor(ada, accountFor(ada));
		String groceries = groceriesFor(ada);

		mvc().perform(post("/api/v1/transactions")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":10000,"currency":"USD",
						 "date":"2026-08-20","paymentMethodId":"%s"}"""
						.formatted(groceries, card)))
				.andExpect(status().isCreated())
				// 100 USD at 1.0858 per EUR is 92.10 EUR, stored beside the 100 that
				// was typed and the rate between them (BR-8).
				.andExpect(jsonPath("$.amount").value(10000))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.amountInDefaultCurrency").value(9210))
				// Bought on the 20th, closing the 25th, due the 5th (BR-4).
				.andExpect(jsonPath("$.plannedExpenseDate").value("2026-09-05"));
	}

	@Test
	@DisplayName("BR-5: an account purchase is not deferred to any bill")
	void anAccountPurchaseIsNotDeferred() throws Exception {
		Cookie ada = register("ada-account-log@example.com");
		String account = accountFor(ada);
		String groceries = groceriesFor(ada);

		mvc().perform(post("/api/v1/transactions")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":5000,"currency":"EUR",
						 "date":"2026-08-20","paymentMethodId":"%s"}"""
						.formatted(groceries, account)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.plannedExpenseDate").doesNotExist())
				.andExpect(jsonPath("$.amountInDefaultCurrency").value(5000));
	}

	@Test
	@DisplayName("BR-8: the rates endpoint states the base and when the table was pulled")
	void theRatesEndpointStatesItsAge() throws Exception {
		Cookie ada = register("ada-rates@example.com");

		mvc().perform(get("/api/v1/fx/rates").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.base").value("EUR"))
				.andExpect(jsonPath("$.rates.USD").value(1.0858))
				.andExpect(jsonPath("$.fetchedAt").value("2026-08-31T07:12:00Z"));
	}

	@Test
	@DisplayName("another user's category cannot be logged against, and answers 404")
	void anotherUsersCategoryCannotBeLoggedAgainst() throws Exception {
		Cookie ada = register("ada-iso-log@example.com");
		Cookie grace = register("grace-iso-log@example.com");

		String adasGroceries = groceriesFor(ada);
		String gracesAccount = accountFor(grace);

		// 404, not 403: a 403 would confirm the category id is real (ADR-11).
		mvc().perform(post("/api/v1/transactions")
				.cookie(grace)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":5000,"currency":"EUR",
						 "date":"2026-08-20","paymentMethodId":"%s"}"""
						.formatted(adasGroceries, gracesAccount)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("BR-6: instalment terms are refused for now rather than dropped")
	void instalmentTermsAreRefusedForNow() throws Exception {
		Cookie ada = register("ada-financing@example.com");
		String account = accountFor(ada);
		String groceries = groceriesFor(ada);

		mvc().perform(post("/api/v1/transactions")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":39900,"currency":"EUR",
						 "date":"2026-08-20","paymentMethodId":"%s",
						 "financing":{"instalmentCount":6,"instalmentAmount":7150,
						 "frequency":"MONTHLY"}}""".formatted(groceries, account)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("financing"));
	}
}
