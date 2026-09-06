package ie.budgetTracker.application.support;

import java.math.BigDecimal;

/**
 * The one place money changes representation on its way out.
 *
 * The application holds BigDecimal at scale 2; the wire carries an integer
 * count of minor units, because that is what `frontend/src/types/api.ts`
 * promises and what keeps JavaScript away from a decimal it cannot represent.
 *
 * `longValueExact` rather than `longValue`: a value that will not convert
 * exactly is a bug upstream, and rounding it here would hide the bug behind a
 * plausible figure.
 */
public final class Money {

	private Money() {
	}

	public static long toMinorUnits(BigDecimal amount) {
		return amount.movePointRight(2).longValueExact();
	}

	public static BigDecimal fromMinorUnits(long minorUnits) {
		return BigDecimal.valueOf(minorUnits, 2);
	}
}
