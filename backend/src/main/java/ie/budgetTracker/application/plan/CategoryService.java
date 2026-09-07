package ie.budgetTracker.application.plan;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.application.plan.dto.CategoryListResponse;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.plan.DateRange;
import ie.budgetTracker.domain.plan.PeriodKind;
import ie.budgetTracker.domain.plan.PeriodResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories, and the plan each of them carries (BR-14).
 *
 * The window a list is read against is resolved here, not on the screen: BR-10
 * counts occurrences on real dates, so which dates the period covers is a
 * business answer and the client is told it rather than left to assume it.
 */
@Service
public class CategoryService {

	private final CategoryRepository categories;
	private final UserRepository users;
	private final CurrentUser currentUser;
	private final Clock clock;

	public CategoryService(CategoryRepository categories, UserRepository users,
			CurrentUser currentUser, Clock clock) {
		this.categories = categories;
		this.users = users;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CategoryListResponse list(String period, LocalDate from, LocalDate to) {
		PeriodKind kind = periodKind(period);
		DateRange window = windowFor(kind, from, to);

		return new CategoryListResponse(
				PeriodWindowResponse.of(kind, window),
				categories.findAllForUser(currentUser.id()).stream()
						.map(CategoryResponse::from)
						.toList());
	}

	/**
	 * BR-14: creating a category creates its planned amount and frequency.
	 *
	 * There is no path through this method that writes one without the other,
	 * which is BR-14 stated as a shape rather than as a check somebody has to
	 * remember to run.
	 */
	@Transactional
	public CategoryResponse create(CreateCategoryRequest request) {
		String name = request.name().trim();
		refuseADuplicate(name, request.type());

		return CategoryResponse.from(categories.create(currentUser.id(), new Category(
				null,
				request.type(),
				name,
				request.group().trim(),
				Money.fromMinorUnits(request.plannedAmount()),
				request.plannedFrequency(),
				request.anchorDate(),
				false)));
	}

	/**
	 * BR-14: the plan is editable inline, one field at a time.
	 *
	 * An unmentioned field keeps what it had. Rewriting it with a null would let
	 * a keystroke in the amount cell wipe the anchor date beside it, and BR-10
	 * would then count a different number of occurrences without anybody having
	 * asked for that.
	 */
	@Transactional
	public CategoryResponse updatePlan(UUID id, UpdateCategoryPlanRequest request) {
		if (request.changesNothing()) {
			throw new AppException(AppException.Kind.INVALID,
					"Say what to change: an amount, a frequency, an anchor date or a group");
		}

		Category existing = categories.findForUser(currentUser.id(), id)
				.orElseThrow(() -> AppException.notFound("No category with id " + id));

		Category edited = new Category(
				existing.id(),
				// Not editable, deliberately: an expense category that became an
				// earning one would take its whole history to the other side of
				// BR-9 and reverse every variance already recorded against it.
				existing.type(),
				existing.name(),
				request.group() == null ? existing.group() : request.group().trim(),
				request.plannedAmount() == null
						? existing.plannedAmount()
						: Money.fromMinorUnits(request.plannedAmount()),
				request.plannedFrequency() == null
						? existing.plannedFrequency()
						: request.plannedFrequency(),
				request.anchorDate() == null ? existing.anchorDate() : request.anchorDate(),
				existing.archived());

		return CategoryResponse.from(categories.update(currentUser.id(), edited));
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
	private DateRange windowFor(PeriodKind kind, LocalDate from, LocalDate to) {
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

	private void refuseADuplicate(String name, CategoryType type) {
		boolean taken = categories.findAllForUser(currentUser.id()).stream()
				.anyMatch(existing -> existing.type() == type
						&& existing.name().equalsIgnoreCase(name));

		if (taken) {
			String kind = type == CategoryType.EXPENSE ? "expense" : "earning";
			throw AppException.conflict(
					"An " + kind + " category called \"" + name + "\" already exists");
		}
	}
}
