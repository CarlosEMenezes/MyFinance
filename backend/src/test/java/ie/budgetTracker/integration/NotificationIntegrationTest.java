package ie.budgetTracker.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The derived queue, from the HTTP request to PostgreSQL and back
 * (spec §6 step 10).
 *
 * "Today" is the 31st of August 2026, so a loan instalment due on the 1st of
 * September is one day out and the lead-time rules can be asserted as numbers.
 */
class NotificationIntegrationTest extends IntegrationTest {

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

	private void aLoanDueOn(Cookie session, String firstDueDate, String account)
			throws Exception {
		mvc().perform(post("/api/v1/loans")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"label":"Credit union","principal":250000,"instalmentCount":24,
						 "instalmentAmount":11840,"frequency":"MONTHLY","instalmentsPaid":5,
						 "firstDueDate":"%s","depositAccountId":"%s"}"""
						.formatted(firstDueDate, account)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("BR-12: the queue is derived from what is owed, with days remaining")
	void theQueueIsDerivedFromWhatIsOwed() throws Exception {
		Cookie ada = register("ada-notif@example.com");
		aLoanDueOn(ada, "2026-09-01", accountFor(ada));

		mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].label").value("Credit union"))
				.andExpect(jsonPath("$[0].daysUntilDue").value(1))
				.andExpect(jsonPath("$[0].sourceType").value("LOAN"))
				.andExpect(jsonPath("$[0].readAt").doesNotExist());
	}

	@Test
	@DisplayName("BR-12: read state is the one thing that survives a recomputation")
	void readStateSurvivesARecomputation() throws Exception {
		Cookie ada = register("ada-notifread@example.com");
		aLoanDueOn(ada, "2026-09-01", accountFor(ada));

		String key = mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andReturn().getResponse().getContentAsString()
				.replaceAll("^\\[\\{\"key\":\"([^\"]+)\".*$", "$1");

		mvc().perform(patch("/api/v1/notifications/read")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"keys":["%s"],"read":true}""".formatted(key)))
				.andExpect(status().isOk())
				// The answer is the queue as it now stands, which is what the page
				// is typed to receive.
				.andExpect(jsonPath("$[0].readAt").exists());

		// The queue is rebuilt from scratch, and the item is still read: the key
		// is stable, which is what read state is stored against.
		mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andExpect(jsonPath("$[0].readAt").exists());

		mvc().perform(patch("/api/v1/notifications/read")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"keys":["%s"],"read":false}""".formatted(key)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].readAt").doesNotExist());

		// Unread is the absence of a row, not a row saying false.
		mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andExpect(jsonPath("$[0].readAt").doesNotExist());
	}

	@Test
	@DisplayName("BR-12: a narrower lead time hides what is further away")
	void aNarrowerLeadTimeHidesWhatIsFurtherAway() throws Exception {
		Cookie ada = register("ada-notiflead@example.com");
		// Ten days out from the 31st of August.
		aLoanDueOn(ada, "2026-09-10", accountFor(ada));

		mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andExpect(jsonPath("$.length()").value(1));

		mvc().perform(patch("/api/v1/notifications/settings")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"leadDays":[2]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leadDays[0]").value(2));

		mvc().perform(get("/api/v1/notifications").cookie(ada))
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	@DisplayName("BR-12: settings default to every lead, so a new account is still warned")
	void settingsDefaultToEveryLead() throws Exception {
		Cookie ada = register("ada-notifsettings@example.com");

		// Somebody who has never opened Settings still gets warned: the useful
		// failure is being told too early rather than not at all.
		mvc().perform(get("/api/v1/notifications/settings").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leadDays.length()").value(3))
				.andExpect(jsonPath("$.channels.push").value(true));
	}

	@Test
	@DisplayName("BR-12: a lead time the rule does not offer is refused")
	void anImpossibleLeadTimeIsRefused() throws Exception {
		Cookie ada = register("ada-notifbadlead@example.com");

		mvc().perform(patch("/api/v1/notifications/settings")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"leadDays":[7]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("leadDays"));
	}

	@Test
	@DisplayName("one user's read state and settings are their own")
	void readStateAndSettingsAreOwnedByOneUser() throws Exception {
		Cookie ada = register("ada-notifiso@example.com");
		Cookie grace = register("grace-notifiso@example.com");

		aLoanDueOn(ada, "2026-09-01", accountFor(ada));

		mvc().perform(get("/api/v1/notifications").cookie(grace))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());

		mvc().perform(patch("/api/v1/notifications/settings")
				.cookie(grace)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"leadDays":[2]}"""))
				.andExpect(status().isOk());

		// Ada asked for nothing, so Ada still has all three.
		mvc().perform(get("/api/v1/notifications/settings").cookie(ada))
				.andExpect(jsonPath("$.leadDays.length()").value(3));
	}
}
