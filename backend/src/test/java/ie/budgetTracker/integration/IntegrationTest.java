package ie.budgetTracker.integration;

import jakarta.servlet.http.Cookie;

import ie.budgetTracker.api.auth.SessionCookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A slice end to end, on real PostgreSQL (spec §4, testing layer five).
 *
 * The other tests run on H2 in PostgreSQL mode, which is close enough for the
 * Flyway migrations to run and not close enough to be believed about check
 * constraints, types or dialect. This is where the schema meets the database it
 * will actually live on.
 *
 * The container is started once for the whole JVM and never stopped, rather
 * than once per class: starting Postgres is the expensive part, and Ryuk
 * removes it when the run ends.
 */
@SpringBootTest
@Import(IntegrationTest.FixedTime.class)
public abstract class IntegrationTest {

	/**
	 * Pinned to the version production runs, not `latest`. A test that silently
	 * changes database version is a test that stops meaning anything.
	 */
	private static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17.5");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void useTheContainer(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
	}

	/**
	 * The date the prototype hard-codes as "today", so an end-to-end run can
	 * assert the exact dates in docs/business-rule-vectors.md rather than
	 * something merely self-consistent.
	 */
	public static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	@TestConfiguration
	static class FixedTime {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private MockMvc mvc;

	@BeforeEach
	void buildMockMvc() {
		// Built with the security chain attached, so an integration test goes
		// through authentication rather than around it (ADR-11).
		mvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	protected MockMvc mvc() {
		return mvc;
	}

	/** Registers someone and returns the session cookie they were given. */
	protected Cookie register(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"a-long-enough-passphrase","name":"Someone"}"""
						.formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		Cookie session = result.getResponse().getCookie(SessionCookie.NAME);
		assertThat(session).as("a session cookie is set on registration").isNotNull();
		return session;
	}

	/** The id out of a creation response, whose first field is always `id`. */
	protected static String idOf(String json) {
		return json.replaceAll("^\\{\"id\":\"([^\"]+)\".*$", "$1");
	}
}
