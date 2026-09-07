package ie.budgetTracker.api.fx;

import ie.budgetTracker.application.fx.FxService;
import ie.budgetTracker.application.fx.dto.FxRatesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR-8's rates, so a screen can say what a converted total was converted at
 * and when.
 */
@RestController
@RequestMapping("/api/v1/fx")
class FxController {

	private final FxService fx;

	FxController(FxService fx) {
		this.fx = fx;
	}

	@GetMapping("/rates")
	FxRatesResponse rates() {
		return fx.rates();
	}
}
