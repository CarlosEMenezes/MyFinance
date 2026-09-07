package ie.budgetTracker.api.dashboard;

import ie.budgetTracker.application.dashboard.DashboardService;
import ie.budgetTracker.application.dashboard.dto.DashboardResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `GET /dashboard?period=MONTH&from=&to=` — the whole overview in one call.
 *
 * Spec §4 names this endpoint and states the reason: the frontend must not
 * recompute business figures. Three screens read the same period, and served
 * from three calls they could disagree about what August contained.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping
	DashboardResponse forPeriod(
			@RequestParam(defaultValue = "MONTH") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate to) {

		return dashboard.forPeriod(period, from, to);
	}
}
