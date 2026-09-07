package ie.budgetTracker.domain.money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * What one currency is worth in another, at a stated moment (BR-8).
 *
 * Rates are units of each currency per one unit of {@code base}, which is the
 * direction providers quote them in. They are kept that way rather than
 * inverted into whatever is convenient at the call site, because a rate shown
 * the other way round is a different number that looks like the same one.
 *
 * There is no fallback and no default. A currency this snapshot cannot price
 * returns empty, and BR-8 makes that block the save: a guessed rate produces a
 * total that is wrong in a way nobody can see.
 */
public record ExchangeRates(Currency base, Map<Currency, BigDecimal> rates, Instant fetchedAt) {

	public ExchangeRates {
		rates = Map.copyOf(rates);
	}

	/**
	 * How many units of {@code to} one unit of {@code from} buys.
	 *
	 * Empty when either side is unpriced, which is the only honest answer: the
	 * caller decides what to do about it, and BR-8 says what that is.
	 */
	public Optional<BigDecimal> rateFrom(Currency from, Currency to) {
		if (from == to) {
			// Exactly one, not a rate that happens to round to one.
			return Optional.of(BigDecimal.ONE);
		}

		BigDecimal perBaseFrom = rates.get(from);
		BigDecimal perBaseTo = rates.get(to);

		if (perBaseFrom == null || perBaseTo == null
				|| perBaseFrom.compareTo(BigDecimal.ZERO) == 0) {
			return Optional.empty();
		}

		return Optional.of(perBaseTo.divide(perBaseFrom, MoneyCalculator.RATE_CONTEXT));
	}

	/** The amount restated in {@code to}, rounded to money once, at the end. */
	public Optional<BigDecimal> convert(BigDecimal amount, Currency from, Currency to) {
		return rateFrom(from, to).map(rate -> MoneyCalculator.of(amount.multiply(rate)));
	}
}
