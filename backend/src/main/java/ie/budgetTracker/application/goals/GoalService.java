package ie.budgetTracker.application.goals;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.goals.dto.CreateGoalRequest;
import ie.budgetTracker.application.goals.dto.GoalResponse;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.goals.Goal;
import ie.budgetTracker.domain.goals.GoalCalculator;
import ie.budgetTracker.domain.goals.GoalPlan;
import ie.budgetTracker.domain.money.MoneyCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Savings goals, with BR-11 solved on the way out.
 *
 * Nothing derived is stored. The gap, the required contribution and the pace
 * marker all move with today's date, so they are computed on every read - and
 * a goal that was on pace yesterday and is not today says so without anything
 * having to be rewritten.
 *
 * The what-if slider is deliberately not an endpoint. Spec §5 names it as a
 * case for the frontend's own pure function, and ADR-7 allows exactly that for
 * money nobody has committed to: a round trip per drag is the reason that
 * exception exists.
 */
@Service
public class GoalService {

	private final GoalRepository goals;
	private final CurrentUser currentUser;
	private final Clock clock;

	public GoalService(GoalRepository goals, CurrentUser currentUser, Clock clock) {
		this.goals = goals;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<GoalResponse> list() {
		LocalDate today = LocalDate.now(clock);

		return goals.findAllForUser(currentUser.id()).stream()
				.map(goal -> describe(goal, today))
				.toList();
	}

	@Transactional
	public GoalResponse create(CreateGoalRequest request) {
		LocalDate today = LocalDate.now(clock);

		if (!request.targetDate().isAfter(today)) {
			// BR-11 divides the gap by the periods left, and a date already past
			// leaves none to divide by.
			throw AppException.invalid("targetDate",
					"Choose a date in the future: a goal needs time left to save in");
		}

		Goal goal = new Goal(
				null,
				request.name().trim(),
				Money.fromMinorUnits(request.targetAmount()),
				request.targetDate(),
				request.savedAmount() == null
						? MoneyCalculator.ZERO
						: Money.fromMinorUnits(request.savedAmount()),
				request.contributionFrequency(),
				request.pocketId(),
				request.rank() == null ? nextRank() : request.rank(),
				today);

		return describe(goals.create(currentUser.id(), goal), today);
	}

	/** BR-11, solved against today rather than against when the goal was made. */
	private static GoalResponse describe(Goal goal, LocalDate today) {
		int months = monthsLeft(goal.targetDate(), today);

		GoalPlan plan = months > 0
				? GoalCalculator.plan(goal.targetAmount(), goal.savedAmount(),
						goal.contributionFrequency(), months)
				// A goal whose date has passed still has a gap worth stating; what
				// it does not have is a contribution schedule, because there is
				// nothing left to spread it over.
				: new GoalPlan(
						MoneyCalculator.atLeastZero(MoneyCalculator.subtract(goal.targetAmount(),
								goal.savedAmount())),
						MoneyCalculator.ZERO, MoneyCalculator.ZERO);

		return GoalResponse.from(goal, plan,
				GoalCalculator.progressPercent(goal.targetAmount(), goal.savedAmount()),
				GoalCalculator.pacePercent(goal.startedOn(), goal.targetDate(), today));
	}

	/**
	 * Whole months to the target, at least one while the date is still ahead.
	 *
	 * Rounded up rather than down: with three weeks left, dividing by zero
	 * months is undefined and dividing by one says what this month has to
	 * carry.
	 */
	private static int monthsLeft(LocalDate targetDate, LocalDate today) {
		if (!targetDate.isAfter(today)) {
			return 0;
		}
		return Math.max(1, (int) ChronoUnit.MONTHS.between(today, targetDate));
	}

	/** A new goal goes on the end of the list rather than into the middle of it. */
	private int nextRank() {
		return goals.findAllForUser(currentUser.id()).stream()
				.mapToInt(Goal::rank)
				.max()
				.orElse(0) + 1;
	}
}
