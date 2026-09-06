package ie.budgetTracker.domain.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Every money value in this application, at scale 2 and rounded HALF_UP
 * (ADR-6, spec §0.5).
 *
 * The frontend holds money as an integer count of minor units and rounds half
 * away from zero, which is what HALF_UP means. The two agree by construction,
 * and docs/business-rule-vectors.md asserts it on both sides.
 *
 * There is no {@code double} anywhere in this class. A rate is not money and
 * may be a {@code double} inside a solver, but a figure a person will read is
 * a BigDecimal from the moment it is parsed.
 */
public final class MoneyCalculator {

	/** Spec §0.5: every money value is held at two decimal places. */
	public static final int SCALE = 2;

	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

	/**
	 * For intermediate work that is not itself money — a rate, a periods-per-month
	 * factor. Kept wide so that a division does not throw for a non-terminating
	 * expansion, and so the rounding to scale 2 happens exactly once, at the end.
	 */
	public static final MathContext RATE_CONTEXT = new MathContext(20, ROUNDING);

	/** Optional sign, digits, optionally a dot and more digits. Nothing else. */
	private static final Pattern AMOUNT = Pattern.compile("^-?\\d+(\\.\\d+)?$");

	private MoneyCalculator() {
	}

	/**
	 * Parses an amount written the way a person writes it.
	 *
	 * @throws IllegalArgumentException if the text is not an amount. A partly
	 *         typed value such as {@code "12."} is refused rather than guessed at.
	 */
	public static BigDecimal of(String amount) {
		String text = amount == null ? "" : amount.trim();
		if (!AMOUNT.matcher(text).matches()) {
			throw new IllegalArgumentException("\"" + amount + "\" is not an amount");
		}
		return new BigDecimal(text).setScale(SCALE, ROUNDING);
	}

	/** Brings an already-numeric value to the canonical scale and rounding. */
	public static BigDecimal of(BigDecimal amount) {
		return amount.setScale(SCALE, ROUNDING);
	}

	public static BigDecimal add(BigDecimal augend, BigDecimal addend) {
		return of(augend.add(addend));
	}

	public static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
		return of(minuend.subtract(subtrahend));
	}

	public static BigDecimal multiply(BigDecimal amount, int factor) {
		return of(amount.multiply(BigDecimal.valueOf(factor)));
	}

	public static BigDecimal multiply(BigDecimal amount, BigDecimal factor) {
		return of(amount.multiply(factor));
	}

	/**
	 * Divides and rounds once, at the end. Dividing at scale 2 and then
	 * multiplying back would compound the rounding error.
	 */
	public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
		return of(dividend.divide(divisor, RATE_CONTEXT));
	}

	public static BigDecimal sum(Collection<BigDecimal> amounts) {
		return of(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	public static BigDecimal negate(BigDecimal amount) {
		return of(amount.negate());
	}

	/** Compares by value, so 0.0 and 0.00 are the same amount. */
	public static boolean isZero(BigDecimal amount) {
		return amount.compareTo(BigDecimal.ZERO) == 0;
	}

	public static boolean isNegative(BigDecimal amount) {
		return amount.compareTo(BigDecimal.ZERO) < 0;
	}

	public static boolean isPositive(BigDecimal amount) {
		return amount.compareTo(BigDecimal.ZERO) > 0;
	}

	/** BR-11: a reached goal has a gap of zero, never a negative one. */
	public static BigDecimal atLeastZero(BigDecimal amount) {
		return isNegative(amount) ? ZERO : of(amount);
	}
}
