package ie.budgetTracker.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.ExchangeRates;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * BR-8's rates as they actually arrive: over HTTP, cached, and refused rather
 * than guessed when they cannot be had.
 *
 * No network is touched. The point of this test is the three behaviours the
 * rule turns on - what is cached, what is served when the provider is down,
 * and what happens when there is nothing to serve at all.
 */
class CachedExchangeRateProviderTest {

	private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");
	private static final String BASE_URL = "https://rates.example";

	private static final String ANSWER = """
			{"amount":1.0,"base":"EUR","date":"2026-08-31",
			 "rates":{"USD":1.0858,"GBP":0.8422,"BRL":5.9134}}""";

	private MockRestServiceServer provider;

	private CachedExchangeRateProvider providerAt(Instant now, Duration cacheFor) {
		RestClient.Builder builder = RestClient.builder();
		provider = MockRestServiceServer.bindTo(builder).build();

		return new CachedExchangeRateProvider(builder, Clock.fixed(now, ZoneOffset.UTC), BASE_URL,
				cacheFor);
	}

	@Test
	@DisplayName("BR-8: reads the provider table and stamps it with when it was pulled")
	void readsTheProviderTable() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ofHours(1));
		provider.expect(requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

		ExchangeRates snapshot = rates.ratesFor(Currency.EUR);

		assertThat(snapshot.base()).isEqualTo(Currency.EUR);
		assertThat(snapshot.rates()).containsEntry(Currency.USD, new BigDecimal("1.0858"));
		// The provider omits the base from its own table; a snapshot without it
		// would make a same-currency conversion look unpriced.
		assertThat(snapshot.rates()).containsEntry(Currency.EUR, BigDecimal.ONE);
		assertThat(snapshot.fetchedAt()).isEqualTo(NOON);
		provider.verify();
	}

	@Test
	@DisplayName("BR-8: caches, so a screen full of conversions is one network call")
	void cachesWithinTheWindow() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ofHours(1));
		provider.expect(ExpectedCount.once(), requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

		rates.ratesFor(Currency.EUR);
		rates.ratesFor(Currency.EUR);
		rates.ratesFor(Currency.EUR);

		// `once` is the assertion: a second call would fail verification.
		provider.verify();
	}

	@Test
	@DisplayName("BR-8: serves the last real rate when the provider is unreachable")
	void servesTheLastRealRateWhenTheProviderIsDown() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ZERO);
		provider.expect(requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
		provider.expect(requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withServerError());

		rates.ratesFor(Currency.EUR);
		ExchangeRates stale = rates.ratesFor(Currency.EUR);

		// Not a guess: a number that was true at a stated time, and the screen
		// shows that time. The alternative is refusing to state a total that is
		// very nearly right.
		assertThat(stale.rates()).containsEntry(Currency.USD, new BigDecimal("1.0858"));
		assertThat(stale.fetchedAt()).isEqualTo(NOON);
		// Both calls really happened: without this the test would pass even if the
		// second answer had come from the cache rather than from the fallback.
		provider.verify();
	}

	@Test
	@DisplayName("BR-8: with nothing cached, a failed lookup is a refusal, not a default")
	void refusesWhenThereIsNothingToServe() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ofHours(1));
		provider.expect(requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withServerError());

		// There is no rate of 1 hiding anywhere in this path. BR-8 blocks the
		// save, because a guessed rate is a wrong total nobody can see is wrong.
		assertThatThrownBy(() -> rates.ratesFor(Currency.EUR))
				.isInstanceOf(AppException.class)
				.satisfies(refused -> assertThat(((AppException) refused).kind())
						.isEqualTo(AppException.Kind.UNAVAILABLE))
				.hasMessageContaining("could not be fetched");
	}

	@Test
	@DisplayName("refuses an answer that carries no rates at all")
	void refusesAnEmptyAnswer() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ofHours(1));
		provider.expect(requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> rates.ratesFor(Currency.EUR))
				.isInstanceOf(AppException.class);
	}

	@Test
	@DisplayName("asks again once the cache window has passed")
	void refreshesAfterTheWindow() {
		CachedExchangeRateProvider rates = providerAt(NOON, Duration.ZERO);
		provider.expect(ExpectedCount.twice(),
				requestTo(Matchers.startsWith(BASE_URL + "/latest")))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

		rates.ratesFor(Currency.EUR);
		rates.ratesFor(Currency.EUR);

		provider.verify();
	}
}
