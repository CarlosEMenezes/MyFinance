package ie.budgetTracker.application.cards;

import ie.budgetTracker.domain.cards.Card;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The port through which cards are read and written.
 *
 * Every method takes the user whose cards are being asked for, and there is no
 * overload that does not. A card has no owner column of its own - it belongs to
 * whoever owns the account it settles from - so this interface is where that
 * fact is stated to the rest of the application (ADR-11).
 */
public interface CardRepository {

	List<Card> findAllForUser(UUID userId);

	Optional<Card> findForUser(UUID userId, UUID cardId);

	/**
	 * Writes a card against an account the user owns.
	 *
	 * An account belonging to somebody else is not found rather than forbidden:
	 * a 403 would confirm the id exists, which is the one thing an id-guesser
	 * wants to know (ADR-11).
	 */
	Card create(UUID userId, Card card);
}
