package ie.budgetTracker.api.plan;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.plan.CategoryService;
import ie.budgetTracker.application.plan.dto.CategoryListResponse;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.PeriodKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of /categories, against the contract
 * frontend/src/types/api.ts already froze (ADR-12).
 *
 * The window travels with the list rather than being assumed by the page,
 * because BR-10 counts occurrences against real dates and those two dates are
 * the difference between four paydays and five.
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerTest extends ie.budgetTracker.api.WebSliceTest {

	private static final UUID RENT = UUID.fromString("44444444-4444-4444-8444-444444444444");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CategoryService categories;

	private static CategoryResponse rent() {
		return CategoryResponse.from(new Category(RENT, CategoryType.EXPENSE, "Rent", "Fixed",
				of("780.00"), Frequency.MONTHLY, LocalDate.parse("2026-01-01"), false));
	}

	private static CategoryListResponse august() {
		return new CategoryListResponse(
				new PeriodWindowResponse(PeriodKind.MONTH, LocalDate.parse("2026-08-01"),
						LocalDate.parse("2026-08-31"), "August 2026"),
				List.of(rent()));
	}

	@Nested
	@DisplayName("listing")
	class Listing {

		@Test
		void statesTheWindowAlongsideTheCategories() throws Exception {
			given(categories.list(eq("MONTH"), any(), any())).willReturn(august());

			mvc.perform(get("/api/v1/categories?period=MONTH").cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.period.kind").value("MONTH"))
					.andExpect(jsonPath("$.period.from").value("2026-08-01"))
					.andExpect(jsonPath("$.period.to").value("2026-08-31"))
					.andExpect(jsonPath("$.period.label").value("August 2026"))
					.andExpect(jsonPath("$.categories[0].name").value("Rent"))
					.andExpect(jsonPath("$.categories[0].group").value("Fixed"))
					// 780.00 crosses as 78000.
					.andExpect(jsonPath("$.categories[0].plannedAmount").value(78000))
					.andExpect(jsonPath("$.categories[0].plannedFrequency").value("MONTHLY"))
					.andExpect(jsonPath("$.categories[0].anchorDate").value("2026-01-01"))
					.andExpect(jsonPath("$.categories[0].archived").value(false));
		}

		@Test
		@DisplayName("defaults to the month, which is what the pages ask for")
		void defaultsToTheMonth() throws Exception {
			given(categories.list(eq("MONTH"), any(), any())).willReturn(august());

			mvc.perform(get("/api/v1/categories").cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.period.kind").value("MONTH"));
		}

		@Test
		void refusesToAnswerWithoutASession() throws Exception {
			mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		@DisplayName("BR-14: creates a category with its plan and answers 201")
		void createsACategory() throws Exception {
			given(categories.create(any(CreateCategoryRequest.class))).willReturn(rent());

			mvc.perform(post("/api/v1/categories")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Rent","type":"EXPENSE","group":"Fixed","plannedAmount":78000,
							 "plannedFrequency":"MONTHLY","anchorDate":"2026-01-01"}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.plannedAmount").value(78000));
		}

		@Test
		@DisplayName("BR-14: refuses a category with no plan, naming the field")
		void refusesACategoryWithNoPlan() throws Exception {
			// A category with no planned frequency is a row the plan-vs-real tables
			// cannot draw, so it is refused rather than defaulted.
			mvc.perform(post("/api/v1/categories")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Rent","type":"EXPENSE","group":"Fixed","plannedAmount":78000,
							 "anchorDate":"2026-01-01"}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("plannedFrequency"));
		}

		@Test
		void answers409WithTheReasonWhenTheNameIsTaken() throws Exception {
			willThrow(AppException.conflict("An expense category called \"Rent\" already exists"))
					.given(categories).create(any(CreateCategoryRequest.class));

			mvc.perform(post("/api/v1/categories")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Rent","type":"EXPENSE","group":"Fixed","plannedAmount":78000,
							 "plannedFrequency":"MONTHLY","anchorDate":"2026-01-01"}"""))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title")
							.value("An expense category called \"Rent\" already exists"));
		}
	}

	@Nested
	@DisplayName("editing the plan inline (BR-14)")
	class Editing {

		@Test
		void savesOneFieldAndAnswersTheWholeCategory() throws Exception {
			given(categories.updatePlan(eq(RENT), any(UpdateCategoryPlanRequest.class)))
					.willReturn(rent());

			// The page replaces its row with what comes back, so a partial answer
			// would blank the fields it did not mention.
			mvc.perform(patch("/api/v1/categories/" + RENT)
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"plannedAmount":80500}"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Rent"))
					.andExpect(jsonPath("$.plannedFrequency").value("MONTHLY"));
		}

		@Test
		void answers404ForACategoryThatIsNotThere() throws Exception {
			willThrow(AppException.notFound("No category with id " + RENT))
					.given(categories).updatePlan(any(), any(UpdateCategoryPlanRequest.class));

			mvc.perform(patch("/api/v1/categories/" + RENT)
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"plannedAmount":80500}"""))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.title").value("No category with id " + RENT));
		}

		@Test
		void refusesANegativePlannedAmount() throws Exception {
			// A planned expense of minus eight hundred is not a plan, and BR-9
			// would colour the variance from it as though it meant something.
			mvc.perform(patch("/api/v1/categories/" + RENT)
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"plannedAmount":-80500}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("plannedAmount"));
		}
	}
}
