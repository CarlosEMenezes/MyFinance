package ie.budgetTracker.application.plan;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import ie.budgetTracker.domain.plan.PeriodKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Categories and the plan they carry (BR-14), with the ports mocked.
 *
 * Which dates a period covers is {@link PeriodWindows}'s question and has its
 * own test; what this covers is that the window travels with the list, and
 * that an edit changes only what was named.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID RENT = UUID.randomUUID();

	@Mock
	private CategoryRepository categories;

	@Mock
	private PeriodWindows periods;

	private CategoryService service;

	@BeforeEach
	void setUp() {
		service = new CategoryService(categories, periods, () -> ADA);
	}

	private void augustIsTheWindow() {
		given(periods.describe("MONTH", null, null)).willReturn(new PeriodWindowResponse(
				PeriodKind.MONTH, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"),
				"August 2026"));
	}

	private static Category rent() {
		return new Category(RENT, CategoryType.EXPENSE, "Rent", "Fixed", of("780.00"),
				Frequency.MONTHLY, LocalDate.parse("2026-01-01"), false);
	}

	@Nested
	@DisplayName("listing against a window")
	class Listing {

		@Test
		@DisplayName("BR-10: the window travels with the list")
		void theWindowTravelsWithTheList() {
			augustIsTheWindow();
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));

			// A weekly plan lands four or five times depending on these two dates,
			// so the client is told them rather than left to assume a month.
			assertThat(service.list("MONTH", null, null).period().from())
					.isEqualTo(LocalDate.parse("2026-08-01"));
			assertThat(service.list("MONTH", null, null).period().label())
					.isEqualTo("August 2026");
		}

		@Test
		void statesMoneyInMinorUnits() {
			augustIsTheWindow();
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));

			CategoryResponse category = service.list("MONTH", null, null).categories().get(0);

			assertThat(category.plannedAmount()).isEqualTo(78000L);
			assertThat(category.plannedFrequency()).isEqualTo(Frequency.MONTHLY);
			assertThat(category.anchorDate()).isEqualTo(LocalDate.parse("2026-01-01"));
		}

		@Test
		@DisplayName("answers archived categories too, and says which they are")
		void answersArchivedCategoriesToo() {
			augustIsTheWindow();
			given(categories.findAllForUser(ADA)).willReturn(List.of(new Category(RENT,
					CategoryType.EXPENSE, "Old gym", "Fixed", of("40.00"), Frequency.MONTHLY,
					LocalDate.parse("2026-01-01"), true)));

			// Hiding them here would make a screen that wants to show them ask for
			// a second endpoint. The flag is the answer; what to draw is the page's
			// business.
			assertThat(service.list("MONTH", null, null).categories().get(0).archived()).isTrue();
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		@DisplayName("BR-14: creating a category creates its planned amount and frequency")
		void createsACategoryWithItsPlan() {
			given(categories.create(eq(ADA), any())).willReturn(rent());

			service.create(new CreateCategoryRequest("Rent", CategoryType.EXPENSE, "Fixed", 78000L,
					Frequency.MONTHLY, LocalDate.parse("2026-01-01")));

			// There is no path that makes a category without a plan, because a
			// category with no plan is a row the plan-vs-real tables cannot draw.
			ArgumentCaptor<Category> written = ArgumentCaptor.forClass(Category.class);
			Mockito.verify(categories).create(eq(ADA), written.capture());
			assertThat(written.getValue().plannedAmount()).isEqualByComparingTo(of("780.00"));
			assertThat(written.getValue().plannedFrequency()).isEqualTo(Frequency.MONTHLY);
			assertThat(written.getValue().archived()).isFalse();
		}

		@Test
		void refusesANameThisUserAlreadyUsesForTheSameKind() {
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));

			assertThatThrownBy(() -> service.create(new CreateCategoryRequest("rent",
					CategoryType.EXPENSE, "Fixed", 78000L, Frequency.MONTHLY,
					LocalDate.parse("2026-01-01"))))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.CONFLICT);
		}

		@Test
		@DisplayName("allows the same name on the other side of the plan")
		void allowsTheSameNameOnTheOtherSideOfThePlan() {
			// "Tutoring" can be both something earned and something paid for, and
			// the two live in different tables on every screen.
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));
			given(categories.create(eq(ADA), any())).willReturn(rent());

			service.create(new CreateCategoryRequest("Rent", CategoryType.EARNING, "Occasional",
					50000L, Frequency.MONTHLY, LocalDate.parse("2026-01-01")));

			Mockito.verify(categories).create(eq(ADA), any());
		}
	}

	@Nested
	@DisplayName("editing the plan inline (BR-14)")
	class Editing {

		@Test
		void changesOnlyWhatTheRequestMentioned() {
			given(categories.findForUser(ADA, RENT)).willReturn(Optional.of(rent()));
			given(categories.update(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			service.updatePlan(RENT, new UpdateCategoryPlanRequest(80500L, null, null, null));

			// The plan is saved one field at a time as it is typed, so an unnamed
			// field means "not mentioned" and must not be overwritten with a null.
			ArgumentCaptor<Category> written = ArgumentCaptor.forClass(Category.class);
			Mockito.verify(categories).update(eq(ADA), written.capture());
			assertThat(written.getValue().plannedAmount()).isEqualByComparingTo(of("805.00"));
			assertThat(written.getValue().plannedFrequency()).isEqualTo(Frequency.MONTHLY);
			assertThat(written.getValue().anchorDate()).isEqualTo(LocalDate.parse("2026-01-01"));
			assertThat(written.getValue().group()).isEqualTo("Fixed");
		}

		@Test
		@DisplayName("BR-10: changing the frequency keeps the anchor it is counted from")
		void changingTheFrequencyKeepsTheAnchor() {
			given(categories.findForUser(ADA, RENT)).willReturn(Optional.of(rent()));
			given(categories.update(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			service.updatePlan(RENT,
					new UpdateCategoryPlanRequest(null, Frequency.WEEKLY, null, null));

			ArgumentCaptor<Category> written = ArgumentCaptor.forClass(Category.class);
			Mockito.verify(categories).update(eq(ADA), written.capture());
			assertThat(written.getValue().plannedFrequency()).isEqualTo(Frequency.WEEKLY);
			assertThat(written.getValue().anchorDate()).isEqualTo(LocalDate.parse("2026-01-01"));
		}

		@Test
		void answersNotFoundForAnIdThisUserDoesNotHave() {
			given(categories.findForUser(ADA, RENT)).willReturn(Optional.empty());

			// ADR-11: a 403 would confirm the id exists.
			assertThatThrownBy(() -> service.updatePlan(RENT,
					new UpdateCategoryPlanRequest(1L, null, null, null)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.NOT_FOUND);
		}

		@Test
		void refusesAnEditThatChangesNothing() {
			// An empty PATCH is a request that cannot be honoured meaningfully, and
			// answering 200 to it would suggest something was saved.
			assertThatThrownBy(() -> service.updatePlan(RENT,
					new UpdateCategoryPlanRequest(null, null, null, null)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.INVALID);
		}
	}
}
