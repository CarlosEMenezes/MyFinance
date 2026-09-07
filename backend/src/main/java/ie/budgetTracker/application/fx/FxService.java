package ie.budgetTracker.application.fx;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.fx.dto.FxRatesResponse;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.identity.UserRepository;
import ie.budgetTracker.domain.money.ExchangeRates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-8's rates, quoted against the currency this user's totals are stated in.
 *
 * The base is the user's default currency rather than the provider's, because
 * every figure on every screen is in that currency and a table quoted against
 * something else would have to be re-based by whoever read it.
 */
@Service
public class FxService {

	private final ExchangeRateProvider provider;
	private final UserRepository users;
	private final CurrentUser currentUser;

	public FxService(ExchangeRateProvider provider, UserRepository users, CurrentUser currentUser) {
		this.provider = provider;
		this.users = users;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public FxRatesResponse rates() {
		return FxRatesResponse.from(currentRates());
	}

	/** The snapshot every conversion in this request is made against. */
	public ExchangeRates currentRates() {
		return provider.ratesFor(users.findById(currentUser.id())
				.orElseThrow(() -> AppException.notFound("No profile for the current user"))
				.defaultCurrency());
	}
}
