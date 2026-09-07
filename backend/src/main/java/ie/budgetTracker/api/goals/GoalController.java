package ie.budgetTracker.api.goals;

import ie.budgetTracker.application.goals.GoalService;
import ie.budgetTracker.application.goals.dto.CreateGoalRequest;
import ie.budgetTracker.application.goals.dto.GoalResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Savings goals (BR-11).
 *
 * There is no what-if endpoint here, on purpose: spec §5 names the slider as a
 * case for the frontend's own pure function, and ADR-7 allows it for money
 * nobody has committed to. A round trip per drag is precisely the cost that
 * exception exists to avoid.
 */
@RestController
@RequestMapping("/api/v1/goals")
class GoalController {

	private final GoalService goals;

	GoalController(GoalService goals) {
		this.goals = goals;
	}

	@GetMapping
	List<GoalResponse> list() {
		return goals.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GoalResponse create(@Valid @RequestBody CreateGoalRequest request) {
		return goals.create(request);
	}
}
