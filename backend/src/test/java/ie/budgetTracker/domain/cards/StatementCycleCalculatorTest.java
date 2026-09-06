package ie.budgetTracker.domain.cards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * BR-4, table-driven over the boundaries spec §4 names.
 *
 * Every row of the first table is a row of docs/business-rule-vectors.md and is
 * asserted identically in frontend/src/lib/statementCycle.test.ts. When the two
 * disagree, one has drifted from that document.
 */
class StatementCycleCalculatorTest {

	@Nested
	@DisplayName("the bill date")
	class BillDate {

		@ParameterizedTest(name = "{0} closing {1} due {2} -> {3} ({4})")
		@CsvSource({
				"2026-08-20, 25,  5, 2026-09-05, before closing so it joins this statement",
				"2026-08-26, 25,  5, 2026-10-05, after closing so it waits for the next",
				"2026-08-05, 10, 28, 2026-08-28, due day after closing day so the bill lands the same month",
				"2026-08-12, 10, 28, 2026-09-28, after closing still same-month due",
				"2026-08-25, 25,  5, 2026-09-05, on the closing day it still joins this statement",
				"2026-08-01,  1, 28, 2026-08-28, earliest possible closing day purchase on it",
				"2026-08-02,  1, 28, 2026-09-28, earliest possible closing day purchase one day late",
				"2026-08-28, 28,  1, 2026-09-01, latest closing day due before it so the bill rolls a month",
				"2026-08-29, 28,  1, 2026-10-01, past the latest closing day so both rolls apply",
				"2026-08-31, 28,  5, 2026-10-05, a 31st purchase in a 31-day month",
				"2026-12-20, 25,  5, 2027-01-05, the due date crosses into the next year",
				"2026-12-26, 25,  5, 2027-02-05, both the statement and the due date cross the year",
				"2028-02-29, 25,  5, 2028-04-05, a leap day after closing rolls to the March statement",
				"2028-02-20, 25,  5, 2028-03-05, a leap-year February before closing bills in March",
				"2027-02-28, 28, 28, 2027-03-28, due day equal to closing day always rolls a month",
				"2027-01-31, 28, 15, 2027-03-15, a 31st rolling into February still bills on the 15th of March",
		})
		void placesSpendOnTheStatementItBelongsTo(String purchase, int closingDay, int dueDay,
				String expectedBill, String why) {
			LocalDate bill = StatementCycleCalculator.billDateFor(
					LocalDate.parse(purchase), new StatementCycle(closingDay, dueDay));

			assertThat(bill).as(why).isEqualTo(LocalDate.parse(expectedBill));
		}

		@Test
		void isNeverThePurchaseDateItself() {
			// The planned-expense date is the computed bill date, never the day the
			// money was spent (BR-4).
			LocalDate purchase = LocalDate.parse("2026-08-05");

			assertThat(StatementCycleCalculator.billDateFor(purchase, new StatementCycle(25, 5)))
					.isNotEqualTo(purchase);
		}

		@Test
		void alwaysLandsOnTheDueDayBecauseOneToTwentyEightExistsInEveryMonth() {
			LocalDate bill = StatementCycleCalculator.billDateFor(
					LocalDate.parse("2027-02-28"), new StatementCycle(28, 28));

			assertThat(bill.getDayOfMonth()).isEqualTo(28);
		}
	}

	@Nested
	@DisplayName("the next due date")
	class NextDueDate {

		@ParameterizedTest(name = "from {0} due day {1} -> {2}")
		@CsvSource({
				"2026-08-01,  5, 2026-08-05",
				"2026-08-05,  5, 2026-08-05",
				"2026-08-31,  5, 2026-09-05",
				"2026-12-20,  5, 2027-01-05",
				"2027-01-29, 28, 2027-02-28",
		})
		void fallsOnTheNextDueDayOnOrAfterTheDate(String from, int dueDay, String expected) {
			assertThat(StatementCycleCalculator.nextDueDateOnOrAfter(LocalDate.parse(from), dueDay))
					.isEqualTo(LocalDate.parse(expected));
		}

		@Test
		void countsTodayWhenTodayIsTheDueDay() {
			LocalDate dueToday = LocalDate.parse("2026-08-05");

			assertThat(StatementCycleCalculator.nextDueDateOnOrAfter(dueToday, 5)).isEqualTo(dueToday);
		}
	}

	@Nested
	@DisplayName("cycle days are 1 to 28")
	class CycleDayRange {

		@ParameterizedTest
		@ValueSource(ints = { 0, -1, 29, 30, 31 })
		void rejectsAClosingDayOutsideTheRange(int closingDay) {
			// BR-4 needs the day to exist in every month, so no clamping rule is
			// needed anywhere. 29, 30 and 31 are refused, not adjusted.
			assertThatThrownBy(() -> new StatementCycle(closingDay, 5))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("closingDay");
		}

		@ParameterizedTest
		@ValueSource(ints = { 0, -1, 29, 30, 31 })
		void rejectsADueDayOutsideTheRange(int dueDay) {
			assertThatThrownBy(() -> new StatementCycle(25, dueDay))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("dueDay");
		}

		@Test
		void acceptsBothEndsOfTheRange() {
			assertThat(new StatementCycle(1, 28)).isNotNull();
			assertThat(new StatementCycle(28, 1)).isNotNull();
		}

		@Test
		void rejectsADueDayOutsideTheRangeOnTheStandaloneLookupToo() {
			assertThatThrownBy(
					() -> StatementCycleCalculator.nextDueDateOnOrAfter(LocalDate.parse("2026-08-01"), 31))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("dueDay");
		}
	}
}
