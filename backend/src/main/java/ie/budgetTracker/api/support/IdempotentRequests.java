package ie.budgetTracker.api.support;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.IdempotencyStore;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Spec §4: a POST that creates a money record may be retried safely.
 *
 * A retry is the ordinary case, not the exotic one - a flaky connection, a
 * double tap, a browser replaying a request. Without this each retry writes a
 * second entry, and every figure built on top of it is quietly wrong while
 * looking entirely correct.
 *
 * Two decisions worth stating:
 *
 *   - **The key is honoured when present, never required.** `lib/http.ts` sends
 *     no such header, and the contract is frozen (ADR-12). Requiring one would
 *     break every page that works today; honouring one means the frontend can
 *     start sending it whenever it likes, with no server change.
 *   - **The stored answer is replayed verbatim.** Re-deriving it could produce
 *     a different one - a rate has moved, a balance has changed - and a retry
 *     that answers differently from the original is not idempotent.
 *
 * It lives in the api layer because it is about HTTP: what a retried request
 * means, and what to hand back. The store behind it is an application port.
 */
@Component
public class IdempotentRequests {

	private final IdempotencyStore store;
	private final CurrentUser currentUser;
	private final ObjectMapper json;

	IdempotentRequests(IdempotencyStore store, CurrentUser currentUser, ObjectMapper json) {
		this.store = store;
		this.currentUser = currentUser;
		this.json = json;
	}

	/**
	 * Runs {@code create} once per key, and answers the same thing thereafter.
	 *
	 * @param key      the client's `Idempotency-Key`, or null when it sent none
	 * @param endpoint what the key was used for, so the same key on a different
	 *                 endpoint is a reported mistake rather than a match
	 */
	public <T> T once(String key, String endpoint, Class<T> answer, Supplier<T> create) {
		if (key == null || key.isBlank()) {
			return create.get();
		}

		Optional<String> alreadyAnswered = store.replay(currentUser.id(), key.trim(), endpoint);
		if (alreadyAnswered.isPresent()) {
			return read(alreadyAnswered.get(), answer);
		}

		T created = create.get();
		store.remember(currentUser.id(), key.trim(), endpoint, write(created));
		return created;
	}

	private String write(Object answer) {
		// The same object is about to be written to the response by the same
		// mapper, so a failure here would fail the response too. Jackson 3 makes
		// this unchecked, so there is nothing to catch and nothing to hide.
		return json.writeValueAsString(answer);
	}

	private <T> T read(String body, Class<T> answer) {
		try {
			return json.readValue(body, answer);
		} catch (JacksonException unreadable) {
			// A stored answer that no longer parses means the shape changed under
			// it. Better to say so than to re-run a write the caller believes has
			// already happened.
			throw AppException.conflict(
					"That request was already made, but its answer can no longer be read");
		}
	}
}
