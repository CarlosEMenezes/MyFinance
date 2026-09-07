package ie.budgetTracker.api.plan;

import ie.budgetTracker.application.plan.CategoryService;
import ie.budgetTracker.application.plan.dto.CategoryListResponse;
import ie.budgetTracker.application.plan.dto.CategoryResponse;
import ie.budgetTracker.application.plan.dto.CreateCategoryRequest;
import ie.budgetTracker.application.plan.dto.UpdateCategoryPlanRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP and nothing else.
 *
 * `period` arrives as the string the query string actually carries, and the
 * application layer turns it into the domain's vocabulary. Binding it straight
 * to the domain enum would have been convenient and would have put this layer
 * on the domain, which the dependency rule forbids - and the translation earns
 * its keep anyway: an unknown value comes back as a 400 naming the parameter
 * rather than as a framework message nobody wrote.
 */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

	private final CategoryService categories;

	CategoryController(CategoryService categories) {
		this.categories = categories;
	}

	/**
	 * The month is the default because it is what every page asks for. `from`
	 * and `to` are read only by a CUSTOM window, which is the one kind that
	 * cannot be worked out from today.
	 */
	@GetMapping
	CategoryListResponse list(
			@RequestParam(defaultValue = "MONTH") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate to) {

		return categories.list(period, from, to);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
		return categories.create(request);
	}

	/**
	 * BR-14: the whole category comes back, not only what changed.
	 *
	 * The page replaces its row with this answer, so a partial one would blank
	 * the fields it did not mention.
	 */
	@PatchMapping("/{id}")
	CategoryResponse updatePlan(@PathVariable UUID id,
			@Valid @RequestBody UpdateCategoryPlanRequest request) {
		return categories.updatePlan(id, request);
	}
}
