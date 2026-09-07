package ie.budgetTracker.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.plan.PeriodKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The window every period-taking endpoint shares (BR-10).
 *
 * It lives in one place because BR-10 counts occurrences against these exact
 * dates: two endpoints resolving the window separately would eventually
 * disagree about how many times a weekly plan lands, and the category list and
 * the dashboard built from it would show different figures for the same month.
 */
@ExtendWith(MockitoExtension.class)
class PeriodWindowsTest {

	/** The date the prototype hard-codes as "today". It is a Monday. */
	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	private static final UUID ADA = UUID.randomUUID();

	@Mock
	private UserRepository users;

	private PeriodWindows periods;

	@BeforeEach
	void setUp() {
		periods = new PeriodWindows(users, () -> ADA, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void weekStartsOn(WeekStart weekStart) {
		given(users.findById(ADA)).willReturn(Optional.of(new User(ADA, "Ada", null, null, null,
				PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY, weekStart,
				new UserPreferences(true, true, false))));
	}

	@Test
	@DisplayName("BR-10: states the dates the window covers, not only its name")
	void statesTheDatesTheWindowCovers() {
		weekStartsOn(WeekStart.MONDAY);

		PeriodWindowResponse window = periods.describe("MONTH", null, null);

		assertThat(window.kind()).isEqualTo(PeriodKind.MONTH);
		assertThat(window.from()).isEqualTo(LocalDate.parse("2026-08-01"));
		assertThat(window.to()).isEqualTo(LocalDate.parse("2026-08-31"));
		assertThat(window.label()).isEqualTo("August 2026");
	}

	@Test
	@DisplayName("BR-10: a week is the user's week, not a fixed one")
	void aWeekIsTheUsersWeek() {
		weekStartsOn(WeekStart.SUNDAY);

		// The same instant, a different window, and therefore possibly a
		// different number of paydays in it.
		assertThat(periods.describe("WEEK", null, null).from())
				.isEqualTo(LocalDate.parse("2026-08-30"));
	}

	@Test
	void takesThePeriodHoweverItWasTyped() {
		weekStartsOn(WeekStart.MONDAY);

		assertThat(periods.describe("month", null, null).label()).isEqualTo("August 2026");
	}

	@Test
	@DisplayName("a period nobody offers is refused, and says what is on offer")
	void anUnknownPeriodIsRefused() {
		// The api layer hands the raw query value straight through, so this is
		// where a typo becomes a sentence rather than a framework message.
		assertThatThrownBy(() -> periods.describe("FORTNIGHT", null, null))
				.isInstanceOf(AppException.class)
				.satisfies(refused -> {
					assertThat(((AppException) refused).field()).isEqualTo("period");
					assertThat(refused).hasMessageContaining("MONTH");
				});
	}

	@Test
	@DisplayName("a custom window is the range it was given, and reads as both ends")
	void aCustomWindowIsTheRangeItWasGiven() {
		PeriodWindowResponse window = periods.describe("CUSTOM", LocalDate.parse("2026-08-10"),
				LocalDate.parse("2026-08-20"));

		assertThat(window.from()).isEqualTo(LocalDate.parse("2026-08-10"));
		assertThat(window.to()).isEqualTo(LocalDate.parse("2026-08-20"));
		assertThat(window.label()).isEqualTo("10 August 2026 to 20 August 2026");
	}

	@Test
	@DisplayName("a custom window with no dates is refused, naming the field")
	void aCustomWindowWithNoDatesIsRefused() {
		// Choosing them here would be the code picking the dates the user asked
		// to pick themselves.
		assertThatThrownBy(() -> periods.describe("CUSTOM", null, null))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).field())
				.isEqualTo("from");
	}

	@Test
	void aCustomWindowWithNoEndIsRefused() {
		assertThatThrownBy(
				() -> periods.describe("CUSTOM", LocalDate.parse("2026-08-10"), null))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).field())
				.isEqualTo("to");
	}

	@Test
	@DisplayName("a custom window that ends before it starts is refused")
	void aBackwardsCustomWindowIsRefused() {
		assertThatThrownBy(() -> periods.describe("CUSTOM", LocalDate.parse("2026-08-20"),
				LocalDate.parse("2026-08-10")))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).field())
				.isEqualTo("to");
	}

	@Test
	void answersNotFoundWhenThereIsNoProfileToReadTheWeekStartFrom() {
		given(users.findById(ADA)).willReturn(Optional.empty());

		assertThatThrownBy(() -> periods.describe("WEEK", null, null))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).kind())
				.isEqualTo(AppException.Kind.NOT_FOUND);
	}
}
