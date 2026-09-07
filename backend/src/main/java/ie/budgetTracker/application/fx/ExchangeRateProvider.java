package ie.budgetTracker.application.fx;

import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;

/**
 * The port BR-8's live rates arrive through.
 *
 * There is deliberately no method that answers "the rate, or a default". An
 * implementation that cannot supply rates throws, and the caller blocks the
 * save. BR-8 is explicit that a failed lookup is a refusal, because a guessed
 * rate turns into a total that is wrong in a way nobody can see.
 */
public interface ExchangeRateProvider {

	/**
	 * The current rates, quoted against {@code base}.
	 *
	 * @throws ie.budgetTracker.application.AppException when no rates can be had
	 *         at all - neither fresh nor previously cached.
	 */
	ExchangeRates ratesFor(Currency base);
}
