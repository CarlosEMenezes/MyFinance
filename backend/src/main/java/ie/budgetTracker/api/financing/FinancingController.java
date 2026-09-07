package ie.budgetTracker.api.financing;

import ie.budgetTracker.application.financing.FinancingService;
import ie.budgetTracker.application.financing.dto.CreateLoanRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPlanResponse;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewRequest;
import ie.budgetTracker.application.financing.dto.InstalmentPreviewResponse;
import ie.budgetTracker.application.financing.dto.LoanPreviewRequest;
import ie.budgetTracker.application.financing.dto.LoanPreviewResponse;
import ie.budgetTracker.application.financing.dto.LoanResponse;
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
 * Instalment plans and loans, and the two preview endpoints spec §4 names.
 *
 * A preview writes nothing. It exists so the log form can show what spreading
 * a purchase or taking a loan would cost before anyone commits to it, and so
 * the frontend's own pure functions have an authoritative answer to agree with
 * (ADR-7).
 *
 * One controller for both, at two paths, because the previews are the same
 * question as the records and splitting them would put the same rule behind
 * two doors.
 */
@RestController
@RequestMapping("/api/v1")
class FinancingController {

	private final FinancingService financing;

	FinancingController(FinancingService financing) {
		this.financing = financing;
	}

	@GetMapping("/instalment-plans")
	List<InstalmentPlanResponse> plans() {
		return financing.plans();
	}

	@GetMapping("/loans")
	List<LoanResponse> loans() {
		return financing.loans();
	}

	/** BR-2: principal, terms and deposit account, in one write. */
	@PostMapping("/loans")
	@ResponseStatus(HttpStatus.CREATED)
	LoanResponse createLoan(@Valid @RequestBody CreateLoanRequest request) {
		return financing.createLoan(request);
	}

	@PostMapping("/instalment-plans/preview")
	InstalmentPreviewResponse previewInstalments(
			@Valid @RequestBody InstalmentPreviewRequest request) {
		return financing.previewInstalments(request);
	}

	@PostMapping("/loans/preview")
	LoanPreviewResponse previewLoan(@Valid @RequestBody LoanPreviewRequest request) {
		return financing.previewLoan(request);
	}
}
