package ie.budgetTracker.application.plan;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.application.plan.dto.CategoryListResponse;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Categories and the plan they carry (BR-14, BR-10), with the ports mocked. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	/** The date the prototype hard-codes as "today". It is a Monday. */
	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID RENT = UUID.randomUUID();

	@Mock
	private CategoryRepository categories;

	@Mock
	private UserRepository users;

	private CategoryService service;

	@BeforeEach
	void setUp() {
		service = new CategoryService(categories, users, () -> ADA,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void adaExists() {
		given(users.findById(ADA)).willReturn(Optional.of(new User(ADA, "Ada", null, null, null,
				PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY,
				WeekStart.MONDAY, new UserPreferences(true, true, false))));
	}

	private static Category rent() {
		return new Category(RENT, CategoryType.EXPENSE, "Rent", "Fixed", of("780.00"),
				Frequency.MONTHLY, LocalDate.parse("2026-01-01"), false);
	}

	@Nested
	@DisplayName("listing against a window")
	class Listing {

		@Test
		@DisplayName("BR-10: states the dates the window covers, not only its name")
		void statesTheDatesTheWindowCovers() {
			adaExists();
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));

			CategoryListResponse list = service.list("MONTH", null, null);

			// A weekly plan lands four or five times depending on these two dates,
			// so the client is told them rather than left to assume a month.
			assertThat(list.period().from()).isEqualTo(LocalDate.parse("2026-08-01"));
			assertThat(list.period().to()).isEqualTo(LocalDate.parse("2026-08-31"));
			assertThat(list.period().label()).isEqualTo("August 2026");
		}

		@Test
		@DisplayName("BR-10: a week is the user's week, not a fixed one")
		void aWeekIsTheUsersWeek() {
			given(users.findById(ADA)).willReturn(Optional.of(new User(ADA, "Ada", null, null, null,
					PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY,
					WeekStart.SUNDAY, new UserPreferences(true, true, false))));
			given(categories.findAllForUser(ADA)).willReturn(List.of());

			assertThat(service.list("WEEK", null, null).period().from())
					.isEqualTo(LocalDate.parse("2026-08-30"));
		}

		@Test
		void statesMoneyInMinorUnits() {
			adaExists();
			given(categories.findAllForUser(ADA)).willReturn(List.of(rent()));

			CategoryResponse category = service.list("MONTH", null, null).categories()
					.get(0);

			assertThat(category.plannedAmount()).isEqualTo(78000L);
			assertThat(category.plannedFrequency()).isEqualTo(Frequency.MONTHLY);
			assertThat(category.anchorDate()).isEqualTo(LocalDate.parse("2026-01-01"));
		}

		@Test
		@DisplayName("answers archived categories too, and says which they are")
		void answersArchivedCategoriesToo() {
			adaExists();
			given(categories.findAllForUser(ADA)).willReturn(List.of(new Category(RENT,
					CategoryType.EXPENSE, "Old gym", "Fixed", of("40.00"), Frequency.MONTHLY,
					LocalDate.parse("2026-01-01"), true)));

			// Hiding them here would make a screen that wants to show them ask for
			// a second endpoint. The flag is the answer; what to draw is the page's
			// business.
			assertThat(service.list("MONTH", null, null).categories().get(0).archived())
					.isTrue();
		}

		@Test
		@DisplayName("a period nobody offers is refused, and says what is on offer")
		void anUnknownPeriodIsRefused() {
			// The api layer hands the raw query value straight through, so this is
			// where a typo becomes a sentence rather than a framework message.
			assertThatThrownBy(() -> service.list("FORTNIGHT", null, null))
					.isInstanceOf(AppException.class)
					.satisfies(refused -> {
						assertThat(((AppException) refused).field()).isEqualTo("period");
						assertThat(refused).hasMessageContaining("MONTH");
					});
		}

		@Test
		@DisplayName("takes the period however it was typed")
		void takesThePeriodHoweverItWasTyped() {
			adaExists();
			given(categories.findAllForUser(ADA)).willReturn(List.of());

			assertThat(service.list("month", null, null).period().label())
					.isEqualTo("August 2026");
		}

		@Test
		@DisplayName("a custom window is the one it was given")
		void aCustomWindowIsTheOneItWasGiven() {
			given(categories.findAllForUser(ADA)).willReturn(List.of());

			CategoryListResponse list = service.list("CUSTOM",
					LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-20"));

			assertThat(list.period().from()).isEqualTo(LocalDate.parse("2026-08-10"));
			assertThat(list.period().to()).isEqualTo(LocalDate.parse("2026-08-20"));
		}

		@Test
		@DisplayName("a custom window with no dates is refused, naming the field")
		void aCustomWindowWithNoDatesIsRefused() {
			// Choosing them here would be the code picking the dates the user asked
			// to pick themselves.
			assertThatThrownBy(() -> service.list("CUSTOM", null, null))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("from");
		}

		@Test
		@DisplayName("a custom window that ends before it starts is refused")
		void aBackwardsCustomWindowIsRefused() {
			assertThatThrownBy(() -> service.list("CUSTOM",
					LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-10")))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("to");
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

			UpdateCategoryPlanRequest change = new UpdateCategoryPlanRequest(80500L, null, null,
					null);
			service.updatePlan(RENT, change);

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
