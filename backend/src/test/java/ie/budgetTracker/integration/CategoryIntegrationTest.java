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
 * Categories and the plan, from the HTTP request to PostgreSQL and back
 * (spec §6 step 5).
 *
 * The window this asserts - 1 to 31 August 2026, "August 2026" - is the one in
 * frontend/src/test/fixtures.ts, and the month the prototype was drawn against.
 * It is also the month that holds five weekly paydays from a 5 January anchor,
 * which is the whole reason BR-10 counts real dates.
 */
class CategoryIntegrationTest extends IntegrationTest {

	private String createRent(Cookie session) throws Exception {
		return mvc().perform(post("/api/v1/categories")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Rent","type":"EXPENSE","group":"Fixed","plannedAmount":78000,
						 "plannedFrequency":"MONTHLY","anchorDate":"2026-01-01"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("BR-14: a category is created with its plan and read back with it")
	void aCategoryIsCreatedWithItsPlan() throws Exception {
		Cookie ada = register("ada-plan@example.com");
		createRent(ada);

		mvc().perform(get("/api/v1/categories?period=MONTH").cookie(ada))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categories[0].name").value("Rent"))
				.andExpect(jsonPath("$.categories[0].plannedAmount").value(78000))
				.andExpect(jsonPath("$.categories[0].plannedFrequency").value("MONTHLY"))
				.andExpect(jsonPath("$.categories[0].anchorDate").value("2026-01-01"));
	}

	@Test
	@DisplayName("BR-10: the window travels with the list, dates and all")
	void theWindowTravelsWithTheList() throws Exception {
		Cookie ada = register("ada-window@example.com");
		createRent(ada);

		// "Month" names the window; only these two dates say where its edges
		// fall, and those edges decide whether a weekly plan lands four or five
		// times.
		mvc().perform(get("/api/v1/categories?period=MONTH").cookie(ada))
				.andExpect(jsonPath("$.period.kind").value("MONTH"))
				.andExpect(jsonPath("$.period.from").value("2026-08-01"))
				.andExpect(jsonPath("$.period.to").value("2026-08-31"))
				.andExpect(jsonPath("$.period.label").value("August 2026"));
	}

	@Test
	@DisplayName("BR-14: an inline plan edit is saved and answered in full")
	void anInlinePlanEditIsSavedAndAnsweredInFull() throws Exception {
		Cookie ada = register("ada-edit@example.com");
		String id = idOf(createRent(ada));

		mvc().perform(patch("/api/v1/categories/" + id)
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"plannedAmount":80500}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plannedAmount").value(80500))
				// Unmentioned fields keep what they had: the page replaces its whole
				// row with this answer.
				.andExpect(jsonPath("$.plannedFrequency").value("MONTHLY"))
				.andExpect(jsonPath("$.anchorDate").value("2026-01-01"))
				.andExpect(jsonPath("$.group").value("Fixed"));

		mvc().perform(get("/api/v1/categories").cookie(ada))
				.andExpect(jsonPath("$.categories[0].plannedAmount").value(80500));
	}

	@Test
	@DisplayName("BR-10: a custom window is the range it was given")
	void aCustomWindowIsTheRangeItWasGiven() throws Exception {
		Cookie ada = register("ada-custom@example.com");
		createRent(ada);

		mvc().perform(get("/api/v1/categories?period=CUSTOM&from=2026-08-10&to=2026-08-20")
				.cookie(ada))
				.andExpect(jsonPath("$.period.from").value("2026-08-10"))
				.andExpect(jsonPath("$.period.to").value("2026-08-20"))
				.andExpect(jsonPath("$.period.label").value("10 August 2026 to 20 August 2026"));
	}

	@Test
	@DisplayName("a custom window with no dates is refused, naming the field")
	void aCustomWindowWithNoDatesIsRefused() throws Exception {
		Cookie ada = register("ada-nodates@example.com");

		mvc().perform(get("/api/v1/categories?period=CUSTOM").cookie(ada))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("from"));
	}

	@Test
	@DisplayName("PostgreSQL itself refuses two categories with one name on one side")
	void postgresRefusesADuplicateNamePerType() throws Exception {
		Cookie ada = register("ada-dupe@example.com");
		createRent(ada);

		// Refused at the service with a sentence worth showing. The unique
		// constraint behind it is what holds when somebody writes SQL by hand.
		mvc().perform(post("/api/v1/categories")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"rent","type":"EXPENSE","group":"Fixed","plannedAmount":10000,
						 "plannedFrequency":"MONTHLY","anchorDate":"2026-01-01"}"""))
				.andExpect(status().isConflict());

		// The same name on the other side of the plan is a different thing, and
		// is allowed.
		mvc().perform(post("/api/v1/categories")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Rent","type":"EARNING","group":"Occasional","plannedAmount":50000,
						 "plannedFrequency":"MONTHLY","anchorDate":"2026-01-01"}"""))
				.andExpect(status().isCreated());
	}
}
