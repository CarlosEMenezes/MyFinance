package ie.budgetTracker.application.support;

import java.util.Optional;
import java.util.UUID;

/**
 * What a client has already been told, so a retry is answered rather than
 * repeated (spec §4).
 *
 * Every method takes the user. A key is chosen by the client, so two people
 * can pick the same one, and a store that did not scope by user would hand the
 * second person the first person's answer (ADR-11).
 */
public interface IdempotencyStore {

	/** The answer already given for this key, if there is one. */
	Optional<String> replay(UUID userId, String key, String endpoint);

	/** Remembers the answer, so the next identical request gets this one. */
	void remember(UUID userId, String key, String endpoint, String responseBody);
}
