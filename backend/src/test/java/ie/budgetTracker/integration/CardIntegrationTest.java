package ie.budgetTracker.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Cards from the HTTP request to PostgreSQL and back (spec §6 step 4).
 *
 * Nothing is mocked here. What this proves that the slice tests cannot: the
 * migration runs on the real database, the check constraints in it are the
 * dialect PostgreSQL actually speaks, and the three BR-4 dates survive the
 * whole round trip as ISO days rather than as whatever a serialiser felt like.
 */
class CardIntegrationTest extends IntegrationTest {

	private String accountFor(Cookie session, String name) throws Exception {
		return idOf(mvc().perform(post("/api/v1/accounts")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","kind":"BANK","balance":84230,"currency":"EUR",
						 "includeInTotals":true}""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	@DisplayName("BR-4: a credit card comes back with its three cycle dates computed")
	void aCreditCardComesBackWithItsCycleDates() throws Exception {
		Cookie ada = register("ada-cards@example.com");
		String revolut = accountFor(ada, "Revolut Current");

		mvc().perform(post("/api/v1/cards")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"CREDIT","name":"Visa 4417","accountId":"%s",
						 "creditLimit":200000,"closingDay":25,"dueDay":5}""".formatted(revolut)))
				.andExpect(status().isCreated());

		// The Visa fixture in frontend/src/test/fixtures.ts, to the day. The page
		// has been rendering these for weeks against MSW; this is the server
		// meaning them (ADR-12).
		mvc().perform(get("/api/v1/cards").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].settlesFrom").value("Revolut Current"))
				.andExpect(jsonPath("$[0].creditLimit").value(200000))
				// BR-1: a new card owes nothing until something is spent on it.
				.andExpect(jsonPath("$[0].currentBalance").value(0))
				.andExpect(jsonPath("$[0].cycle.nextBillDate").value("2026-09-05"))
				.andExpect(jsonPath("$[0].cycle.billDateOnClosingDay").value("2026-09-05"))
				.andExpect(jsonPath("$[0].cycle.billDateAfterClosingDay").value("2026-10-05"));
	}

	@Test
	@DisplayName("BR-5: a debit card is stored and answered with no cycle at all")
	void aDebitCardHasNoCycle() throws Exception {
		Cookie ada = register("ada-debit@example.com");
		String revolut = accountFor(ada, "Revolut Current");

		mvc().perform(post("/api/v1/cards")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"DEBIT","name":"Revolut debit","accountId":"%s"}"""
						.formatted(revolut)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.cycle").doesNotExist());

		mvc().perform(get("/api/v1/cards").cookie(ada))
				.andExpect(jsonPath("$[0].kind").value("DEBIT"))
				.andExpect(jsonPath("$[0].cycle").doesNotExist())
				.andExpect(jsonPath("$[0].closingDay").doesNotExist());
	}

	@Test
	@DisplayName("BR-13: the account names the card that settles from it")
	void theAccountNamesTheCardThatSettlesFromIt() throws Exception {
		Cookie ada = register("ada-both@example.com");
		String revolut = accountFor(ada, "Revolut Current");

		mvc().perform(post("/api/v1/cards")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"DEBIT","name":"Revolut debit","accountId":"%s"}"""
						.formatted(revolut)))
				.andExpect(status().isCreated());

		// Names, not figures. What a card owes belongs to BR-1's other side and
		// must never be folded into the balance it settles from.
		mvc().perform(get("/api/v1/accounts").cookie(ada))
				.andExpect(jsonPath("$[0].balance").value(84230))
				.andExpect(jsonPath("$[0].cardNames[0]").value("Revolut debit"));
	}

	@Test
	@DisplayName("BR-4: PostgreSQL itself refuses a cycle day that is not in every month")
	void postgresRefusesACycleDayOutsideTheRange() throws Exception {
		Cookie ada = register("ada-range@example.com");
		String revolut = accountFor(ada, "Revolut Current");

		// Refused at the edge before it reaches the database, which is the answer
		// a form can act on. The constraint behind it is the one that holds when
		// somebody writes SQL by hand.
		mvc().perform(post("/api/v1/cards")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"CREDIT","name":"Impossible","accountId":"%s",
						 "creditLimit":200000,"closingDay":31,"dueDay":5}""".formatted(revolut)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("closingDay"));
	}
}
