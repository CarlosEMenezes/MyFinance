package ie.budgetTracker.api.goals;

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
import ie.budgetTracker.application.goals.GoalService;
import ie.budgetTracker.application.goals.dto.CreateGoalRequest;
import ie.budgetTracker.application.goals.dto.GoalResponse;
import ie.budgetTracker.domain.goals.ContributionFrequency;
import ie.budgetTracker.domain.goals.Goal;
import ie.budgetTracker.domain.goals.GoalCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The wire shape of `/goals`, against the frozen contract (ADR-12). */
@WebMvcTest(GoalController.class)
class GoalControllerTest extends ie.budgetTracker.api.WebSliceTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private GoalService goals;

	private static GoalResponse macbook() {
		Goal goal = new Goal(UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111"),
				"MacBook Air M4", of("1200.00"), LocalDate.parse("2026-12-31"), of("600.00"),
				ContributionFrequency.MONTHLY, null, 1, LocalDate.parse("2026-01-01"));

		return GoalResponse.from(goal,
				GoalCalculator.plan(goal.targetAmount(), goal.savedAmount(),
						goal.contributionFrequency(), 4),
				50, 66);
	}

	@Test
	@DisplayName("BR-11: answers the gap, the contribution, the progress and the pace")
	void answersEverythingBr11Works() throws Exception {
		given(goals.list()).willReturn(List.of(macbook()));

		mvc.perform(get("/api/v1/goals").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("MacBook Air M4"))
				.andExpect(jsonPath("$[0].targetAmount").value(120000))
				.andExpect(jsonPath("$[0].targetDate").value("2026-12-31"))
				.andExpect(jsonPath("$[0].savedAmount").value(60000))
				.andExpect(jsonPath("$[0].gap").value(60000))
				.andExpect(jsonPath("$[0].contributionPerPeriod").value(15000))
				.andExpect(jsonPath("$[0].monthlyRequirement").value(15000))
				.andExpect(jsonPath("$[0].progressPercent").value(50))
				// The marker is what makes the bar a judgement rather than a
				// decoration: half saved, two thirds of the time gone.
				.andExpect(jsonPath("$[0].pacePercent").value(66))
				.andExpect(jsonPath("$[0].onPace").value(false));
	}

	@Test
	void answersAnEmptyListRatherThanNothing() throws Exception {
		given(goals.list()).willReturn(List.of());

		mvc.perform(get("/api/v1/goals").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void createsAGoalAndAnswers201() throws Exception {
		given(goals.create(any(CreateGoalRequest.class))).willReturn(macbook());

		mvc.perform(post("/api/v1/goals")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"MacBook Air M4","targetAmount":120000,"targetDate":"2026-12-31",
						 "savedAmount":60000,"contributionFrequency":"MONTHLY","rank":1}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gap").value(60000));
	}

	@Test
	@DisplayName("BR-11: a target date with no time left is a 400 that names the field")
	void aTargetDateInThePastNamesTheField() throws Exception {
		willThrow(AppException.invalid("targetDate",
				"Choose a date in the future: a goal needs time left to save in"))
				.given(goals).create(any(CreateGoalRequest.class));

		mvc.perform(post("/api/v1/goals")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Too late","targetAmount":120000,"targetDate":"2020-01-01",
						 "contributionFrequency":"MONTHLY"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("targetDate"));
	}

	@Test
	void refusesAGoalWithNothingToSaveFor() throws Exception {
		mvc.perform(post("/api/v1/goals")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Nothing","targetAmount":0,"targetDate":"2026-12-31",
						 "contributionFrequency":"MONTHLY"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("targetAmount"));
	}

	@Test
	void refusesToAnswerWithoutASession() throws Exception {
		mvc.perform(get("/api/v1/goals")).andExpect(status().isUnauthorized());
	}
}
