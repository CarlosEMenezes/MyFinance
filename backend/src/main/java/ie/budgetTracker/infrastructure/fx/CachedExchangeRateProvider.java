package ie.budgetTracker.infrastructure.fx;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.fx.ExchangeRateProvider;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * BR-8's live rates, pulled from a provider and cached (spec BR-8).
 *
 * Frankfurter is the default because it publishes European Central Bank
 * reference rates, needs no API key, and therefore adds no credential to hold.
 * The base URL is configurable, so swapping providers is configuration rather
 * than a code change.
 *
 * Three behaviours worth being explicit about:
 *
 *   - **Cached, with the age published.** A rate does not move enough in an
 *     hour to justify a network call per keystroke, and `fetchedAt` travels
 *     with the snapshot so nothing has to pretend it is live.
 *   - **A stale rate is still a real rate.** If the provider cannot be reached
 *     and a previous snapshot exists, that snapshot is served with its own
 *     `fetchedAt`. That is not guessing: it is a number that was true at a
 *     stated time, and the screen says when.
 *   - **No rates at all is a refusal.** With nothing cached, this throws and
 *     BR-8 blocks the save. There is no default of 1 anywhere in this file.
 */
@Component
class CachedExchangeRateProvider implements ExchangeRateProvider {

	private final RestClient http;
	private final Clock clock;
	private final Duration cacheFor;
	private final String baseUrl;

	private final Map<Currency, ExchangeRates> cache = new ConcurrentHashMap<>();

	CachedExchangeRateProvider(RestClient.Builder http, Clock clock,
			@Value("${budgettracker.fx.base-url:https://api.frankfurter.app}") String baseUrl,
			@Value("${budgettracker.fx.cache-for:PT1H}") Duration cacheFor) {
		this.http = http.build();
		this.clock = clock;
		this.baseUrl = baseUrl;
		this.cacheFor = cacheFor;
	}

	@Override
	public ExchangeRates ratesFor(Currency base) {
		ExchangeRates cached = cache.get(base);
		if (cached != null && !isStale(cached)) {
			return cached;
		}

		try {
			ExchangeRates fresh = fetch(base);
			cache.put(base, fresh);
			return fresh;
		} catch (RuntimeException unreachable) {
			if (cached != null) {
				// A rate that was true an hour ago, labelled with when it was true,
				// beats refusing to show a total that is very nearly right.
				return cached;
			}
			throw AppException.unavailable(
					"Exchange rates could not be fetched, so the amount cannot be converted. "
							+ "Try again in a moment.");
		}
	}

	/**
	 * Stale the moment the window has elapsed, not a moment after.
	 *
	 * The boundary matters: a cache window of zero has to mean "ask every time",
	 * and the obvious `isBefore` spelling makes it mean the opposite.
	 */
	private boolean isStale(ExchangeRates rates) {
		return !Instant.now(clock).isBefore(rates.fetchedAt().plus(cacheFor));
	}

	/**
	 * The provider's answer, as this application's snapshot.
	 *
	 * `fetchedAt` is when the call was made rather than the date the provider
	 * stamped its table with, because the question a screen asks is "how old is
	 * what I am looking at".
	 */
	private ExchangeRates fetch(Currency base) {
		FrankfurterResponse answer = http.get()
				.uri(baseUrl + "/latest?base={base}&symbols={symbols}", base.name(), symbols(base))
				.retrieve()
				.body(FrankfurterResponse.class);

		if (answer == null || answer.rates() == null) {
			throw new IllegalStateException("the rate provider answered with no rates");
		}

		Map<Currency, BigDecimal> rates = new EnumMap<>(Currency.class);
		// One unit of the base is one unit of the base. Providers omit it, and a
		// table without it would make a same-currency conversion look unpriced.
		rates.put(base, BigDecimal.ONE);
		for (Currency currency : Currency.values()) {
			BigDecimal rate = answer.rates().get(currency.name());
			if (rate != null) {
				rates.put(currency, rate);
			}
		}

		return new ExchangeRates(base, rates, Instant.now(clock));
	}

	/** Every currency the app knows, minus the one being quoted against. */
	private static String symbols(Currency base) {
		return java.util.Arrays.stream(Currency.values())
				.filter(currency -> currency != base)
				.map(Enum::name)
				.reduce((left, right) -> left + "," + right)
				.orElse("");
	}

	/** Only the field this application uses; the provider sends more. */
	record FrankfurterResponse(Map<String, BigDecimal> rates) {
	}
}
