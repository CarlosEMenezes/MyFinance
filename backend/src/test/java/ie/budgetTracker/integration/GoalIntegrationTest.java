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
 * Goals from the HTTP request to PostgreSQL and back (spec §6 step 9).
 *
 * The clock is fixed at the prototype's own "today", so the pace marker can be
 * asserted as a number rather than as a range.
 */
class GoalIntegrationTest extends IntegrationTest {

	private void createGoal(Cookie session, String body) throws Exception {
		mvc().perform(post("/api/v1/goals")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("BR-11: a goal comes back with its gap and its contribution worked out")
	void aGoalComesBackWithItsPlan() throws Exception {
		Cookie ada = register("ada-goal@example.com");

		createGoal(ada, """
				{"name":"MacBook Air M4","targetAmount":120000,"targetDate":"2026-12-31",
				 "savedAmount":60000,"contributionFrequency":"MONTHLY","rank":1}""");

		mvc().perform(get("/api/v1/goals").cookie(ada))
				.andExpect(status().isOk())
				// 1,200 target, 600 saved, four months to the end of December.
				.andExpect(jsonPath("$[0].gap").value(60000))
				.andExpect(jsonPath("$[0].contributionPerPeriod").value(15000))
				.andExpect(jsonPath("$[0].progressPercent").value(50))
				// Made today, so the pace clock starts at nothing and nothing is
				// yet behind.
				.andExpect(jsonPath("$[0].pacePercent").value(0))
				.andExpect(jsonPath("$[0].onPace").value(true));
	}

	@Test
	@DisplayName("BR-11: goals come back in the order they were ranked")
	void goalsComeBackRanked() throws Exception {
		Cookie ada = register("ada-goalrank@example.com");

		createGoal(ada, """
				{"name":"Interrail","targetAmount":80000,"targetDate":"2027-06-01",
				 "contributionFrequency":"WEEKLY","rank":2}""");
		createGoal(ada, """
				{"name":"Emergency fund","targetAmount":300000,"targetDate":"2027-12-01",
				 "contributionFrequency":"MONTHLY","rank":1}""");

		mvc().perform(get("/api/v1/goals").cookie(ada))
				.andExpect(jsonPath("$[0].name").value("Emergency fund"))
				.andExpect(jsonPath("$[1].name").value("Interrail"));
	}

	@Test
	@DisplayName("BR-11: a target date with no time left is refused, naming the field")
	void aTargetDateWithNoTimeLeftIsRefused() throws Exception {
		Cookie ada = register("ada-goalpast@example.com");

		mvc().perform(post("/api/v1/goals")
				.cookie(ada)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Too late","targetAmount":120000,"targetDate":"2026-01-01",
						 "contributionFrequency":"MONTHLY"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("targetDate"));
	}

	@Test
	@DisplayName("a user sees only their own goals")
	void aUserSeesOnlyTheirOwnGoals() throws Exception {
		Cookie ada = register("ada-goaliso@example.com");
		Cookie grace = register("grace-goaliso@example.com");

		createGoal(ada, """
				{"name":"MacBook Air M4","targetAmount":120000,"targetDate":"2026-12-31",
				 "contributionFrequency":"MONTHLY","rank":1}""");

		mvc().perform(get("/api/v1/goals").cookie(ada))
				.andExpect(jsonPath("$.length()").value(1));
		mvc().perform(get("/api/v1/goals").cookie(grace))
				.andExpect(jsonPath("$").isEmpty());
	}
}
