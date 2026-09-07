package ie.budgetTracker.application.plan;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.plan.dto.CategoryListResponse;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.PeriodWindowResponse;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories, and the plan each of them carries (BR-14).
 *
 * The window a list is read against comes from {@link PeriodWindows}, shared
 * with every other endpoint that takes a period: BR-10 counts occurrences on
 * real dates, so two places resolving the window separately would eventually
 * disagree about how many times a weekly plan lands.
 */
@Service
public class CategoryService {

	private final CategoryRepository categories;
	private final PeriodWindows periods;
	private final CurrentUser currentUser;

	public CategoryService(CategoryRepository categories, PeriodWindows periods,
			CurrentUser currentUser) {
		this.categories = categories;
		this.periods = periods;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public CategoryListResponse list(String period, LocalDate from, LocalDate to) {
		PeriodWindowResponse window = periods.describe(period, from, to);

		return new CategoryListResponse(window,
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
