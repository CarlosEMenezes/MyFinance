package ie.budgetTracker.application.goals;

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
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.goals.dto.CreateGoalRequest;
import ie.budgetTracker.application.goals.dto.GoalResponse;
import ie.budgetTracker.domain.goals.ContributionFrequency;
import ie.budgetTracker.domain.goals.Goal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BR-11 as the API states it, with the port mocked. */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

	/** The date the prototype hard-codes as "today". */
	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");
	private static final LocalDate TODAY = LocalDate.parse("2026-08-31");

	private static final UUID ADA = UUID.randomUUID();

	@Mock
	private GoalRepository goals;

	private GoalService service;

	@BeforeEach
	void setUp() {
		service = new GoalService(goals, () -> ADA, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	/** Half saved, and exactly halfway through its window. */
	private static Goal macbook(String saved) {
		return new Goal(UUID.randomUUID(), "MacBook Air M4", of("1200.00"),
				LocalDate.parse("2026-12-31"), of(saved), ContributionFrequency.MONTHLY, null, 1,
				LocalDate.parse("2026-01-01"));
	}

	private GoalResponse listed(Goal goal) {
		given(goals.findAllForUser(ADA)).willReturn(List.of(goal));
		return service.list().get(0);
	}

	@Nested
	@DisplayName("what reaching it takes")
	class ThePlan {

		@Test
		@DisplayName("BR-11: states the gap and what it takes per period to close it")
		void statesTheGapAndTheContribution() {
			GoalResponse goal = listed(macbook("600.00"));

			// 1,200 less 600 saved, over the four months to the end of December.
			assertThat(goal.gap()).isEqualTo(60000L);
			assertThat(goal.contributionPerPeriod()).isEqualTo(15000L);
			assertThat(goal.monthlyRequirement()).isEqualTo(15000L);
		}

		@Test
		@DisplayName("BR-11: a reached goal needs nothing, rather than a negative contribution")
		void aReachedGoalNeedsNothing() {
			GoalResponse goal = listed(macbook("1500.00"));

			// A negative gap would become a negative contribution, which is not
			// advice anybody can act on.
			assertThat(goal.gap()).isZero();
			assertThat(goal.contributionPerPeriod()).isZero();
			assertThat(goal.progressPercent()).isEqualTo(100);
		}

		@Test
		@DisplayName("BR-11: a goal whose date has passed still states its gap")
		void aGoalPastItsDateStillStatesItsGap() {
			GoalResponse goal = listed(new Goal(UUID.randomUUID(), "Interrail", of("800.00"),
					LocalDate.parse("2026-06-01"), of("300.00"), ContributionFrequency.WEEKLY,
					null, 2, LocalDate.parse("2026-01-01")));

			// What is missing is still worth saying; what it no longer has is a
			// schedule, because there is nothing left to spread the gap over.
			assertThat(goal.gap()).isEqualTo(50000L);
			assertThat(goal.contributionPerPeriod()).isZero();
		}
	}

	@Nested
	@DisplayName("BR-11, the pace marker")
	class Pace {

		@Test
		@DisplayName("says a goal is behind when the saving has not kept up with the calendar")
		void saysAGoalIsBehind() {
			// Eight months of a twelve-month window gone, and a quarter saved.
			GoalResponse goal = listed(macbook("300.00"));

			assertThat(goal.progressPercent()).isEqualTo(25);
			assertThat(goal.pacePercent()).isGreaterThan(60);
			assertThat(goal.onPace()).isFalse();
		}

		@Test
		void saysAGoalIsOnPaceWhenTheSavingHasKeptUp() {
			GoalResponse goal = listed(macbook("1100.00"));

			assertThat(goal.onPace()).isTrue();
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		@DisplayName("BR-11: starts the pace clock today, not at the target date")
		void startsThePaceClockToday() {
			given(goals.create(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			GoalResponse goal = service.create(new CreateGoalRequest("MacBook Air M4", 120000L,
					LocalDate.parse("2026-12-31"), 0L, ContributionFrequency.MONTHLY, null, 1));

			// A goal made today has made no progress and is behind by nothing.
			assertThat(goal.pacePercent()).isZero();
			assertThat(goal.onPace()).isTrue();
			assertThat(goal.gap()).isEqualTo(120000L);
		}

		@Test
		@DisplayName("BR-11: refuses a target date with no time left to save in")
		void refusesATargetDateInThePast() {
			// BR-11 divides the gap by the periods left, and a date already past
			// leaves none to divide by.
			assertThatThrownBy(() -> service.create(new CreateGoalRequest("Too late", 120000L,
					TODAY, 0L, ContributionFrequency.MONTHLY, null, 1)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("targetDate");
		}

		@Test
		@DisplayName("a goal with no rank goes on the end, not into the middle")
		void aGoalWithNoRankGoesOnTheEnd() {
			given(goals.findAllForUser(ADA)).willReturn(List.of(macbook("0.00")));
			given(goals.create(eq(ADA), any())).willAnswer(call -> call.getArgument(1));

			GoalResponse goal = service.create(new CreateGoalRequest("Interrail", 80000L,
					LocalDate.parse("2027-06-01"), null, ContributionFrequency.WEEKLY, null,
					null));

			assertThat(goal.rank()).isEqualTo(2);
			assertThat(goal.savedAmount()).isZero();
		}
	}
}
