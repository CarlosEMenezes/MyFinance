package ie.budgetTracker.application.plan;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.plan.DateRange;
import ie.budgetTracker.domain.plan.PeriodKind;
import ie.budgetTracker.domain.plan.PeriodResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Which dates a requested period covers, in one place.
 *
 * Shared by every endpoint that takes a `period` parameter. Two of them
 * resolving the window separately would eventually resolve it differently, and
 * BR-10 counts occurrences against those exact dates - so a category list and
 * the dashboard built from it could disagree about how many times a weekly
 * plan lands.
 */
@Component
public class PeriodWindows {

	private final UserRepository users;
	private final CurrentUser currentUser;
	private final Clock clock;

	public PeriodWindows(UserRepository users, CurrentUser currentUser, Clock clock) {
		this.users = users;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	/** The window, and how it reads, for a request that named a period. */
	public PeriodWindowResponse describe(String period, LocalDate from, LocalDate to) {
		PeriodKind kind = periodKind(period);
		return PeriodWindowResponse.of(kind, resolve(kind, from, to));
	}

	/**
	 * The wire value, in the domain's vocabulary.
	 *
	 * Done here rather than by binding the query parameter straight to the enum,
	 * because the api layer may not name a domain type - and because an unknown
	 * period is then a 400 that names the parameter and lists what it accepts,
	 * which a framework conversion error does not.
	 */
	private static PeriodKind periodKind(String period) {
		try {
			return PeriodKind.valueOf(period.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw AppException.invalid("period",
					"Ask for one of DAY, WEEK, MONTH, YEAR or CUSTOM");
		}
	}

	/**
	 * BR-10: which dates the window actually covers.
	 *
	 * CUSTOM is the one kind that cannot be derived, so it is validated rather
	 * than resolved - and it does not read the profile at all, because a week
	 * start has nothing to say about a range somebody typed.
	 */
	private DateRange resolve(PeriodKind kind, LocalDate from, LocalDate to) {
		if (kind != PeriodKind.CUSTOM) {
			return PeriodResolver.resolve(kind, LocalDate.now(clock), weekStart());
		}

		if (from == null) {
			throw AppException.invalid("from", "A custom period needs the date it starts on");
		}
		if (to == null) {
			throw AppException.invalid("to", "A custom period needs the date it ends on");
		}
		if (to.isBefore(from)) {
			throw AppException.invalid("to", "A period cannot end before it starts");
		}
		return new DateRange(from, to);
	}

	/** A week begins where the user says it does, which moves its edges. */
	private WeekStart weekStart() {
		return users.findById(currentUser.id())
				.orElseThrow(() -> AppException.notFound("No profile for the current user"))
				.weekStart();
	}
}
