package ie.budgetTracker.application.support;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The seam where money changes representation on its way to the wire.
 *
 * ADR-6: BigDecimal at scale 2 inside, an integer count of minor units on the
 * wire, because that is what frontend/src/types/api.ts promises and what keeps
 * JavaScript away from a decimal it cannot represent.
 */
class MoneyTest {

	@ParameterizedTest(name = "{0} crosses as {1}")
	@CsvSource({
			"1450.00, 145000",
			"842.30,   84230",
			"0.00,         0",
			"0.01,         1",
			"-135.58,  -13558",
	})
	@DisplayName("states an amount as minor units")
	void statesAnAmountAsMinorUnits(String amount, long expected) {
		assertThat(Money.toMinorUnits(of(amount))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} arrives as {1}")
	@CsvSource({
			"145000, 1450.00",
			"84230,   842.30",
			"0,         0.00",
			"-13558, -135.58",
	})
	@DisplayName("reads minor units back at scale 2")
	void readsMinorUnitsBack(long minorUnits, String expected) {
		assertThat(Money.fromMinorUnits(minorUnits)).isEqualTo(new BigDecimal(expected));
	}

	@Test
	@DisplayName("round-trips without drift")
	void roundTrips() {
		BigDecimal original = of("2841.60");

		assertThat(Money.fromMinorUnits(Money.toMinorUnits(original))).isEqualTo(original);
	}

	@Test
	@DisplayName("refuses an amount that would not convert exactly")
	void refusesAnInexactAmount() {
		// A third decimal place here is a bug upstream. Rounding it away would
		// hide that bug behind a figure that looks entirely plausible.
		assertThatThrownBy(() -> Money.toMinorUnits(new BigDecimal("120.005")))
				.isInstanceOf(ArithmeticException.class);
	}
}
