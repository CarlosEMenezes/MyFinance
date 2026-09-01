package ie.budgetTracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regression test for the Spring Boot 4 migration hazard recorded in CLAUDE.md:
 * Boot 4 moved Flyway auto-configuration out of {@code spring-boot-autoconfigure}
 * into the {@code spring-boot-flyway} module, so {@code flyway-core} on its own
 * leaves migrations silently unapplied — no error, no log line, no schema.
 */
@SpringBootTest
class FlywayBaselineTest {

	@Autowired
	private Flyway flyway;

	@Test
	void baselineMigrationIsAppliedOnStartup() {
		MigrationInfo[] applied = flyway.info().applied();

		assertThat(applied)
				.as("Flyway must apply the baseline; if this is empty the auto-configuration is missing")
				.isNotEmpty()
				.extracting(info -> info.getVersion().toString())
				.contains("1");
	}
}
