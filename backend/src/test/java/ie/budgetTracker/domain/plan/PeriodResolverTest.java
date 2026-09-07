package ie.budgetTracker.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import ie.budgetTracker.domain.identity.WeekStart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which dates a named period actually covers (BR-10).
 *
 * This is not cosmetic. BR-10 counts occurrences against real dates, so where
 * the window starts and ends decides whether a weekly plan lands four times or
 * five - and the difference is a month's rent.
 */
class PeriodResolverTest {

	/** The date the prototype hard-codes as "today". It is a Monday. */
	private static final LocalDate TODAY = LocalDate.parse("2026-08-31");

	@Nested
	@DisplayName("a day, a month and a year")
	class WholeCalendarUnits {

		@Test
		void aDayIsThatOneDayAtBothEnds() {
			// Inclusive at both ends, like every DateRange: a plan landing on the
			// day is a cost on that day.
			assertThat(PeriodResolver.resolve(PeriodKind.DAY, TODAY, WeekStart.MONDAY))
					.isEqualTo(new DateRange(TODAY, TODAY));
		}

		@Test
		void aMonthRunsFromTheFirstToTheLast() {
			assertThat(PeriodResolver.resolve(PeriodKind.MONTH, TODAY, WeekStart.MONDAY))
					.isEqualTo(new DateRange(LocalDate.parse("2026-08-01"),
							LocalDate.parse("2026-08-31")));
		}

		@Test
		void aMonthEndsOnTheLastDayThatMonthActuallyHas() {
			// February, and a leap February, rather than a fixed 30 or 31.
			assertThat(PeriodResolver.resolve(PeriodKind.MONTH, LocalDate.parse("2027-02-10"),
					WeekStart.MONDAY).end()).isEqualTo(LocalDate.parse("2027-02-28"));

			assertThat(PeriodResolver.resolve(PeriodKind.MONTH, LocalDate.parse("2028-02-10"),
					WeekStart.MONDAY).end()).isEqualTo(LocalDate.parse("2028-02-29"));
		}

		@Test
		void aYearRunsFromJanuaryToDecember() {
			assertThat(PeriodResolver.resolve(PeriodKind.YEAR, TODAY, WeekStart.MONDAY))
					.isEqualTo(new DateRange(LocalDate.parse("2026-01-01"),
							LocalDate.parse("2026-12-31")));
		}
	}

	@Nested
	@DisplayName("a week, which starts where the user says it does")
	class Weeks {

		@Test
		void startsOnMondayForSomebodyWhoseWeekStartsOnMonday() {
			// The 31st is itself a Monday, so the week starts on the day.
			assertThat(PeriodResolver.resolve(PeriodKind.WEEK, TODAY, WeekStart.MONDAY))
					.isEqualTo(new DateRange(LocalDate.parse("2026-08-31"),
							LocalDate.parse("2026-09-06")));
		}

		@Test
		void startsOnSundayForSomebodyWhoseWeekStartsOnSunday() {
			// The same instant, a different window, and therefore possibly a
			// different number of paydays in it.
			assertThat(PeriodResolver.resolve(PeriodKind.WEEK, TODAY, WeekStart.SUNDAY))
					.isEqualTo(new DateRange(LocalDate.parse("2026-08-30"),
							LocalDate.parse("2026-09-05")));
		}

		@Test
		void looksBackwardsWhenTodayIsNotTheFirstDayOfTheWeek() {
			// A Sunday, for somebody whose week begins on Monday: the window is the
			// week that is ending, not the one about to begin.
			assertThat(PeriodResolver.resolve(PeriodKind.WEEK, LocalDate.parse("2026-09-06"),
					WeekStart.MONDAY))
					.isEqualTo(new DateRange(LocalDate.parse("2026-08-31"),
							LocalDate.parse("2026-09-06")));
		}

		@Test
		void isAlwaysSevenDaysLong() {
			DateRange week = PeriodResolver.resolve(PeriodKind.WEEK, TODAY, WeekStart.SUNDAY);

			assertThat(week.end()).isEqualTo(week.start().plusDays(6));
		}
	}

	@Nested
	@DisplayName("a custom window")
	class Custom {

		@Test
		void refusesToInventOneFromNothing() {
			// A custom period is the range it was given. Resolving one from "today"
			// would be the code choosing dates the user asked to choose themselves.
			assertThatThrownBy(
					() -> PeriodResolver.resolve(PeriodKind.CUSTOM, TODAY, WeekStart.MONDAY))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("CUSTOM");
		}
	}
}
