package ie.budgetTracker.application.fx.dto;

import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * `GET /fx/rates` — BR-8.
 *
 * `fetchedAt` is not decoration. Rates are cached, so the honest thing to
 * publish alongside them is when they were actually pulled; a screen that
 * shows a converted total without saying how old the rate is invites the
 * figure to be trusted more than it deserves.
 *
 * The rates are published in the direction the provider quotes them - units of
 * each currency per one unit of `base` - because a rate stated the other way
 * round is a different number that looks like the same one.
 */
public record FxRatesResponse(Currency base, Map<Currency, BigDecimal> rates, Instant fetchedAt) {

	public static FxRatesResponse from(ExchangeRates rates) {
		return new FxRatesResponse(rates.base(), rates.rates(), rates.fetchedAt());
	}
}
