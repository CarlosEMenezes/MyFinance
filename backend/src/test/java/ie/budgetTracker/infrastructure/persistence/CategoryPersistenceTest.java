package ie.budgetTracker.infrastructure.persistence;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.auth.CredentialsRepository;
import ie.budgetTracker.application.plan.CategoryRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The persistence side of BR-14, against the real schema on H2.
 *
 * A category carries its plan, so storing one and storing its planned amount
 * are the same write. There is no path here that creates a category without
 * one, which is the rule stated as a shape rather than as a check.
 */
@DataJpaTest
@Import({ JpaCategoryRepository.class, JpaCredentialsRepository.class,
		CategoryPersistenceTest.Time.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CategoryPersistenceTest {

	@TestConfiguration
	static class Time {
		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}
	}

	@Autowired
	private CategoryRepository categories;

	@Autowired
	private CredentialsRepository credentials;

	private UUID owner;

	@BeforeEach
	void registerAnOwner() {
		owner = register("owner@example.com");
	}

	private UUID register(String email) {
		return credentials.register(email, "{noop}irrelevant", new User(null, "Someone", null, null,
				null, PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY,
				WeekStart.MONDAY, new UserPreferences(true, true, false))).id();
	}

	private Category rent() {
		return new Category(null, CategoryType.EXPENSE, "Rent", "Fixed", of("780.00"),
				Frequency.MONTHLY, LocalDate.parse("2026-01-01"), false);
	}

	@Test
	@DisplayName("BR-14: stores a category with the plan it was created with")
	void storesACategoryWithItsPlan() {
		categories.create(owner, rent());

		assertThat(categories.findAllForUser(owner)).singleElement().satisfies(category -> {
			assertThat(category.name()).isEqualTo("Rent");
			assertThat(category.group()).isEqualTo("Fixed");
			assertThat(category.plannedAmount()).isEqualTo(of("780.00"));
			assertThat(category.plannedFrequency()).isEqualTo(Frequency.MONTHLY);
			assertThat(category.anchorDate()).isEqualTo(LocalDate.parse("2026-01-01"));
			assertThat(category.archived()).isFalse();
			assertThat(category.id()).isNotNull();
		});
	}

	@Test
	@DisplayName("BR-10: keeps the anchor date, which decides how many times a plan lands")
	void keepsTheAnchorDate() {
		// A weekly plan anchored to the 1st and one anchored to the 5th cost
		// different amounts in the same month. The anchor is not decoration.
		categories.create(owner, new Category(null, CategoryType.EARNING, "Tutoring",
				"Self-employed", of("160.00"), Frequency.WEEKLY, LocalDate.parse("2026-01-05"),
				false));

		assertThat(categories.findAllForUser(owner)).singleElement()
				.satisfies(category -> assertThat(category.anchorDate())
						.isEqualTo(LocalDate.parse("2026-01-05")));
	}

	@Test
	@DisplayName("BR-14: an inline plan edit is stored and read back")
	void storesAPlanEdit() {
		Category stored = categories.create(owner, rent());

		categories.update(owner, new Category(stored.id(), stored.type(), stored.name(),
				stored.group(), of("805.00"), stored.plannedFrequency(), stored.anchorDate(),
				stored.archived()));

		assertThat(categories.findForUser(owner, stored.id()))
				.get()
				.satisfies(category -> assertThat(category.plannedAmount()).isEqualTo(of("805.00")));
	}

	@Test
	@DisplayName("keeps categories belonging to another user out of the list")
	void isolatesUsers() {
		categories.create(owner, rent());
		UUID someoneElse = register("someone-else@example.com");

		// ADR-11: the filter is the only thing separating two people's plans, so
		// it is asserted against a real second user rather than a made-up id.
		assertThat(categories.findAllForUser(someoneElse)).isEmpty();
	}

	@Test
	@DisplayName("will not let one user edit another user's plan")
	void willNotLetOneUserEditAnothersPlan() {
		Category stored = categories.create(owner, rent());
		UUID intruder = register("intruder@example.com");

		// Not found rather than forbidden: a 403 would confirm the id is real.
		assertThatThrownBy(() -> categories.update(intruder, new Category(stored.id(),
				stored.type(), stored.name(), stored.group(), of("1.00"),
				stored.plannedFrequency(), stored.anchorDate(), false)))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).kind())
				.isEqualTo(AppException.Kind.NOT_FOUND);
	}

	@Test
	@DisplayName("finds one category for its owner and nobody else")
	void findsOneForItsOwner() {
		Category stored = categories.create(owner, rent());

		assertThat(categories.findForUser(owner, stored.id())).isPresent();
		assertThat(categories.findForUser(register("nosy@example.com"), stored.id())).isEmpty();
	}

	@Test
	@DisplayName("orders categories by name so a list does not reshuffle itself")
	void ordersCategoriesByName() {
		categories.create(owner, rent());
		categories.create(owner, new Category(null, CategoryType.EXPENSE, "Groceries", "Variable",
				of("400.00"), Frequency.WEEKLY, LocalDate.parse("2026-01-03"), false));

		assertThat(categories.findAllForUser(owner)).extracting(Category::name)
				.containsExactly("Groceries", "Rent");
	}
}
