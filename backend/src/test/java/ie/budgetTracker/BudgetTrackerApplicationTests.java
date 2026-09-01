package ie.budgetTracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BudgetTrackerApplicationTests {

	/**
	 * Proves the whole wiring stands up: the context starts, the H2 test
	 * datasource resolves and Flyway applies the baseline migration.
	 */
	@Test
	void applicationContextStartsAndMigrationsApply() {
	}

}
