package ie.budgetTracker.domain.notifications;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BR-12, against the prototype's own due queue and a "today" of 31-08-2026.
 *
 * The same queue the frontend's Notifications page renders, which is why the
 * days-remaining figures here match src/test/notifications.fixture.ts exactly.
 */
class DuePaymentQueueTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-08-31");

	private static DuePayment payment(String key, String date, String amount, DueSource source) {
		return new DuePayment(key, key, "", LocalDate.parse(date), of(amount), source);
	}

	/** The prototype's queue, deliberately out of order. */
	private static List<DuePayment> prototypeQueue() {
		return List.of(
				payment("gym", "2026-09-12", "29", DueSource.DIRECT_DEBIT),
				payment("rent", "2026-09-01", "780", DueSource.DIRECT_DEBIT),
				payment("loan", "2026-09-06", "118.40", DueSource.LOAN),
				payment("card", "2026-09-05", "386.40", DueSource.CARD_BILL),
				payment("subs", "2026-09-03", "14.98", DueSource.SUBSCRIPTION));
	}

	@Nested
	@DisplayName("the order")
	class Order {

		@Test
		void sortsAscendingByDaysRemaining() {
			List<QueuedPayment> queue =
					DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10, 5, 2), Set.of());

			assertThat(queue).extracting(QueuedPayment::key)
					.containsExactly("rent", "subs", "card", "loan");
		}

		@Test
		void statesHowManyDaysAreLeftOnEachItem() {
			List<QueuedPayment> queue =
					DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10, 5, 2), Set.of());

			assertThat(queue).extracting(QueuedPayment::daysUntilDue).containsExactly(1, 3, 5, 6);
		}

		@Test
		void keepsATieInAStableOrderRatherThanAnArbitraryOne() {
			List<DuePayment> sameDay = List.of(
					payment("b", "2026-09-05", "10", DueSource.INSTALMENT),
					payment("a", "2026-09-05", "20", DueSource.INSTALMENT));

			assertThat(DuePaymentQueue.build(sameDay, TODAY, Set.of(10), Set.of()))
					.extracting(QueuedPayment::key).containsExactly("b", "a");
		}
	}

	@Nested
	@DisplayName("the lead-time filter")
	class LeadTimeFilter {

		@Test
		void showsOnlyWhatFallsInsideTheWidestEnabledLeadTime() {
			// The gym at twelve days is outside the widest lead time of ten.
			assertThat(DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10, 5, 2), Set.of()))
					.extracting(QueuedPayment::key).doesNotContain("gym");
		}

		@Test
		void narrowsAsLeadTimesAreTurnedOff() {
			assertThat(DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(5, 2), Set.of()))
					.extracting(QueuedPayment::key).containsExactly("rent", "subs", "card");
			assertThat(DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(2), Set.of()))
					.extracting(QueuedPayment::key).containsExactly("rent");
		}

		@Test
		void stillShowsWhatIsDueTodayWhenEveryLeadTimeIsOff() {
			// Turning every warning off must not hide money that is leaving now.
			List<DuePayment> dueToday = List.of(payment("rent", "2026-08-31", "780",
					DueSource.DIRECT_DEBIT));

			assertThat(DuePaymentQueue.build(dueToday, TODAY, Set.of(), Set.of())).hasSize(1);
		}

		@Test
		void alwaysShowsSomethingOverdueWhateverTheLeadTimes() {
			// Overdue is past every lead time, and is exactly what must not vanish.
			List<DuePayment> overdue = List.of(payment("rent", "2026-08-28", "780",
					DueSource.DIRECT_DEBIT));
			List<QueuedPayment> queue = DuePaymentQueue.build(overdue, TODAY, Set.of(), Set.of());

			assertThat(queue).hasSize(1);
			assertThat(queue.get(0).daysUntilDue()).isEqualTo(-3);
			assertThat(queue.get(0).overdue()).isTrue();
		}

		@Test
		void rejectsALeadTimeThatIsNotOneOfTheThreeAllowed() {
			assertThatThrownBy(
					() -> DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(7), Set.of()))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("lead");
		}
	}

	@Nested
	@DisplayName("what each lead time catches")
	class LeadTimeCounts {

		@Test
		void countsOverTheWholeQueueNotTheVisiblePart() {
			// The count says what turning an option ON would add, so counting only
			// what is already shown would make every enabled option report itself.
			// Four of the five, because the gym at twelve days is beyond ten.
			assertThat(DuePaymentQueue.countCaughtBy(prototypeQueue(), TODAY, 10)).isEqualTo(4);
			assertThat(DuePaymentQueue.countCaughtBy(prototypeQueue(), TODAY, 5)).isEqualTo(3);
			assertThat(DuePaymentQueue.countCaughtBy(prototypeQueue(), TODAY, 2)).isEqualTo(1);
		}

		@Test
		void excludesOverdueItemsWhichArePastEveryLeadTime() {
			List<DuePayment> withOverdue = List.of(
					payment("late", "2026-08-28", "50", DueSource.DIRECT_DEBIT),
					payment("rent", "2026-09-01", "780", DueSource.DIRECT_DEBIT));

			assertThat(DuePaymentQueue.countCaughtBy(withOverdue, TODAY, 10)).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("read state")
	class ReadState {

		@Test
		void marksTheItemsThatHaveBeenRead() {
			List<QueuedPayment> queue =
					DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10), Set.of("rent", "card"));

			assertThat(queue).filteredOn(QueuedPayment::read).extracting(QueuedPayment::key)
					.containsExactly("rent", "card");
		}

		@Test
		void countsWhatIsUnreadAmongWhatIsShown() {
			// BR-12: this count drives the nav badge, so it must not include items
			// the lead times are hiding.
			List<QueuedPayment> queue =
					DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10), Set.of("rent"));

			assertThat(DuePaymentQueue.unreadCount(queue)).isEqualTo(3);
		}

		@Test
		void keepsAReadItemInTheQueueRatherThanRemovingIt() {
			// Read state is per item and persisted; marking something read must not
			// make it unfindable.
			List<QueuedPayment> queue = DuePaymentQueue.build(prototypeQueue(), TODAY, Set.of(10),
					Set.of("rent", "subs", "card", "loan"));

			assertThat(queue).hasSize(4);
			assertThat(DuePaymentQueue.unreadCount(queue)).isZero();
		}
	}
}
