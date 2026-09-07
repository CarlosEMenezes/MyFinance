package ie.budgetTracker.domain.money;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BR-8, the arithmetic half: what one currency is worth in another.
 *
 * The rates here are the prototype's own fixed table, which is also what
 * frontend/src/test/settings.fixture.ts serves, so a figure asserted on one
 * side can be compared with the other.
 */
class ExchangeRatesTest {

	private static final Instant PULLED_AT = Instant.parse("2026-08-31T07:12:00Z");

	private static ExchangeRates prototypeTable() {
		return new ExchangeRates(Currency.EUR, Map.of(
				Currency.EUR, BigDecimal.ONE,
				Currency.USD, new BigDecimal("1.0858"),
				Currency.GBP, new BigDecimal("0.8422"),
				Currency.BRL, new BigDecimal("5.9134")), PULLED_AT);
	}

	@Nested
	@DisplayName("the rate")
	class Rate {

		@Test
		void isExactlyOneBetweenACurrencyAndItself() {
			// Not a rate that happens to round to one: no conversion happened, and
			// the stored fxRate should say so.
			assertThat(prototypeTable().rateFrom(Currency.BRL, Currency.BRL))
					.contains(BigDecimal.ONE);
		}

		@Test
		void readsStraightOffTheTableWhenConvertingFromTheBase() {
			assertThat(prototypeTable().rateFrom(Currency.EUR, Currency.USD))
					.hasValueSatisfying(rate ->
							assertThat(rate).isEqualByComparingTo(new BigDecimal("1.0858")));
		}

		@Test
		void invertsTheTableWhenConvertingIntoTheBase() {
			// 1 / 5.9134 = 0.16910745... A real is quoted the other way round, and
			// this is the one place that division happens.
			assertThat(prototypeTable().rateFrom(Currency.BRL, Currency.EUR))
					.hasValueSatisfying(rate -> assertThat(rate)
							.isCloseTo(new BigDecimal("0.16910745"),
									org.assertj.core.data.Offset.offset(new BigDecimal("0.00000001"))));
		}

		@Test
		void crossesTwoNonBaseCurrenciesThroughTheBase() {
			// USD to GBP: 0.8422 / 1.0858.
			assertThat(prototypeTable().rateFrom(Currency.USD, Currency.GBP))
					.hasValueSatisfying(rate -> assertThat(rate)
							.isCloseTo(new BigDecimal("0.77565"),
									org.assertj.core.data.Offset.offset(new BigDecimal("0.00001"))));
		}

		@Test
		@DisplayName("BR-8: a currency the table cannot price has no rate, not a guess")
		void aCurrencyWithNoRateAnswersNothing() {
			ExchangeRates partial = new ExchangeRates(Currency.EUR,
					Map.of(Currency.EUR, BigDecimal.ONE), PULLED_AT);

			// Empty, so the caller has to decide. BR-8 says what it decides: the
			// save is blocked. A default of 1 here would be a wrong total that
			// nobody could see was wrong.
			assertThat(partial.rateFrom(Currency.USD, Currency.EUR)).isEmpty();
			assertThat(partial.rateFrom(Currency.EUR, Currency.USD)).isEmpty();
		}
	}

	@Nested
	@DisplayName("the converted amount")
	class Conversion {

		@Test
		void roundsToMoneyOnceAtTheEnd() {
			// 100 USD at 1.0858 per EUR is 92.10 EUR, rounded once rather than at
			// each step of the division.
			assertThat(prototypeTable().convert(of("100.00"), Currency.USD, Currency.EUR))
					.hasValueSatisfying(amount ->
							assertThat(amount).isEqualByComparingTo(of("92.10")));
		}

		@Test
		void leavesAnAmountAloneWhenNoConversionIsNeeded() {
			assertThat(prototypeTable().convert(of("842.30"), Currency.EUR, Currency.EUR))
					.hasValueSatisfying(amount ->
							assertThat(amount).isEqualByComparingTo(of("842.30")));
		}

		@Test
		void keepsTheCanonicalScaleSoTheWireCarriesWholeMinorUnits() {
			assertThat(prototypeTable().convert(of("50.00"), Currency.BRL, Currency.EUR))
					.hasValueSatisfying(amount -> assertThat(amount.scale())
							.isEqualTo(MoneyCalculator.SCALE));
		}

		@Test
		void answersNothingWhenTheRateIsMissing() {
			ExchangeRates partial = new ExchangeRates(Currency.EUR,
					Map.of(Currency.EUR, BigDecimal.ONE), PULLED_AT);

			assertThat(partial.convert(of("10.00"), Currency.USD, Currency.EUR)).isEmpty();
		}
	}

	@Test
	@DisplayName("BR-8: says when it was pulled, because freshness is part of the answer")
	void saysWhenItWasPulled() {
		assertThat(prototypeTable().fetchedAt()).isEqualTo(PULLED_AT);
	}

	@Test
	void cannotBeChangedAfterItIsBuilt() {
		Map<Currency, BigDecimal> mutable = new java.util.HashMap<>();
		mutable.put(Currency.EUR, BigDecimal.ONE);
		ExchangeRates snapshot = new ExchangeRates(Currency.EUR, mutable, PULLED_AT);

		// A snapshot that could be edited after the fact is not a snapshot, and
		// two transactions logged a second apart could disagree about the rate.
		mutable.put(Currency.USD, new BigDecimal("999"));

		assertThat(snapshot.rates()).hasSize(1);
	}
}
