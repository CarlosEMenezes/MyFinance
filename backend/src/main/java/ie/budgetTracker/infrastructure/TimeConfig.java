package ie.budgetTracker.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time as a dependency.
 *
 * Injected rather than read from a static call, so a test can decide what
 * "now" is - which BR-4, BR-11 and BR-12 all need, since every one of them
 * answers a question about how far away something is.
 */
@Configuration
class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
