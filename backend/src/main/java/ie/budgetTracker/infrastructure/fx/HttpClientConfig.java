package ie.budgetTracker.infrastructure.fx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client the FX provider calls out with.
 *
 * Declared here rather than relied on from auto-configuration: Spring Boot 4
 * moved auto-configuration into per-technology modules, and a builder that
 * quietly is not there would leave the application failing to start for a
 * reason unrelated to anything in this package (CLAUDE.md gotcha 10). If Boot
 * does supply one, this stands aside.
 */
@Configuration
class HttpClientConfig {

	@Bean
	@ConditionalOnMissingBean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
