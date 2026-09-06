package ie.budgetTracker.domain.plan;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BR-10, counted on real dates. Every row is a row of
 * docs/business-rule-vectors.md, asserted identically in
 * frontend/src/lib/period.test.ts.
 *
 * The rule this file exists to protect: a month holding five paydays plans
 * five. Averaging is BR-3's job and lives on {@link Frequency}, under a name
 * that says so.
 */
class PlanNormaliserTest {

	private static final DateRange AUGUST_2026 =
			new DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

	private static DateRange range(String start, String end) {
		return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
	}

	@Nested
	@DisplayName("counting monthly plans")
	class Monthly {

		@ParameterizedTest(name = "anchor {0} in {1}..{2} lands {3} times ({4})")
		@CsvSource({
				"2026-01-01, 2026-08-01, 2026-08-31,  1, one occurrence in a single month",
				"2026-01-01, 2026-01-01, 2026-12-31, 12, every month of a year",
				"2026-01-31, 2026-01-01, 2026-12-31, 12, a 31st anchor clamps and still lands every month",
				"2026-01-31, 2026-09-01, 2026-09-30,  1, a 31st anchor lands on the last day of a 30-day month",
				"2026-09-15, 2026-08-01, 2026-08-31,  0, a new plan is never backdated",
				"2026-08-12, 2026-08-01, 2026-08-31,  1, the anchor month itself counts",
				"2026-01-12, 2026-08-01, 2026-08-11,  0, an occurrence just outside the range is excluded",
		})
		void countsRealDates(String anchor, String start, String end, int expected, String why) {
			assertThat(PlanNormaliser.occurrencesIn(Frequency.MONTHLY, LocalDate.parse(anchor),
					range(start, end))).as(why).isEqualTo(expected);
		}

		@Test
		void placesAThirtyFirstAnchorOnTheLastDayOfAShortMonth() {
			// Clamped from the original anchor each time, never by stepping from an
			// already-clamped date: 31 Jan + 1 month + 1 month must be 31 March, not
			// 28 March.
			assertThat(PlanNormaliser.occurrencesIn(Frequency.MONTHLY, LocalDate.parse("2026-01-31"),
					range("2026-03-29", "2026-03-31"))).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("counting weekly and fortnightly plans")
	class Stepped {

		@ParameterizedTest(name = "{0} anchor {1} in {2}..{3} lands {4} times ({5})")
		@CsvSource({
				"WEEKLY,      2026-01-05, 2026-08-01, 2026-08-31, 5, the five-payday month, not 52/12",
				"WEEKLY,      2026-01-05, 2026-02-01, 2026-02-28, 4, a month that holds four",
				"WEEKLY,      2026-08-20, 2026-08-01, 2026-08-31, 2, counted from an anchor inside the range",
				"WEEKLY,      2026-09-07, 2026-08-01, 2026-08-31, 0, an anchor after the range",
				"WEEKLY,      2026-01-05, 2026-10-01, 2026-10-31, 4, unaffected by the daylight-saving change",
				"FORTNIGHTLY, 2026-01-05, 2026-08-01, 2026-08-31, 3, stepped from the anchor not the weekly count halved",
				"FORTNIGHTLY, 2026-01-12, 2026-08-01, 2026-08-31, 2, a different anchor alignment",
		})
		void countsRealDates(Frequency frequency, String anchor, String start, String end,
				int expected, String why) {
			assertThat(PlanNormaliser.occurrencesIn(frequency, LocalDate.parse(anchor),
					range(start, end))).as(why).isEqualTo(expected);
		}

		@Test
		void countsTheAnchorItselfOnASingleDayRange() {
			DateRange oneDay = range("2026-08-31", "2026-08-31");

			assertThat(PlanNormaliser.occurrencesIn(Frequency.WEEKLY, LocalDate.parse("2026-01-05"),
					oneDay)).isEqualTo(1);
			assertThat(PlanNormaliser.occurrencesIn(Frequency.WEEKLY, LocalDate.parse("2026-01-06"),
					oneDay)).isZero();
		}

		@Test
		void countsFivePaydaysRatherThanTheAverage() {
			// The whole point of BR-10. The average would say 4.33.
			int real = PlanNormaliser.occurrencesIn(Frequency.WEEKLY, LocalDate.parse("2026-01-05"),
					AUGUST_2026);

			assertThat(real).isEqualTo(5);
			assertThat(Frequency.WEEKLY.periodsPerMonth().doubleValue()).isLessThan(real);
		}
	}

	@Nested
	@DisplayName("the planned amount for a period")
	class PlannedAmount {

		@Test
		void multipliesThePerOccurrenceAmountByHowManyTimesItActuallyLands() {
			// 160.00 a week, five paydays in August 2026, so 800.00 - not the 693.33
			// the BR-3 average would give.
			assertThat(PlanNormaliser.plannedAmountIn(of("160"), Frequency.WEEKLY,
					LocalDate.parse("2026-01-05"), AUGUST_2026)).isEqualTo(of("800.00"));
		}

		@Test
		void isZeroWhenThePlanDoesNotLandInTheRangeAtAll() {
			assertThat(PlanNormaliser.plannedAmountIn(of("160"), Frequency.WEEKLY,
					LocalDate.parse("2026-09-07"), AUGUST_2026)).isEqualTo(of("0.00"));
		}

		@Test
		void matchesThePerOccurrenceAmountWhenItLandsExactlyOnce() {
			assertThat(PlanNormaliser.plannedAmountIn(of("780"), Frequency.MONTHLY,
					LocalDate.parse("2026-01-01"), AUGUST_2026)).isEqualTo(of("780.00"));
		}
	}

	@Nested
	@DisplayName("an unusable range")
	class InvalidRange {

		@Test
		void rejectsARangeThatEndsBeforeItStarts() {
			assertThatThrownBy(
					() -> new DateRange(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-08-01")))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("end");
		}

		@Test
		void acceptsASingleDayRange() {
			assertThat(range("2026-08-31", "2026-08-31")).isNotNull();
		}
	}
}
