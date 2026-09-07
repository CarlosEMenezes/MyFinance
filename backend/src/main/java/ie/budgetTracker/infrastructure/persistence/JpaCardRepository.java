package ie.budgetTracker.infrastructure.persistence;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.domain.cards.Card;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link CardRepository}. */
@Repository
class JpaCardRepository implements CardRepository {

	private final CardJpaRepository cards;
	private final AccountJpaRepository accounts;

	JpaCardRepository(CardJpaRepository cards, AccountJpaRepository accounts) {
		this.cards = cards;
		this.accounts = accounts;
	}

	@Override
	public List<Card> findAllForUser(UUID userId) {
		return cards.findByAccountUserIdOrderByNameAsc(userId).stream()
				.map(CardEntity::toDomain)
				.toList();
	}

	@Override
	public Optional<Card> findForUser(UUID userId, UUID cardId) {
		return cards.findByAccountUserIdAndId(userId, cardId).map(CardEntity::toDomain);
	}

	@Override
	public Card create(UUID userId, Card card) {
		// Looked up by owner AND id, not by id alone. An account belonging to
		// somebody else is simply not found here, so a card cannot be attached to
		// one by guessing its id (ADR-11).
		AccountEntity settlesFrom = accounts.findByUserIdAndId(userId, card.accountId())
				.orElseThrow(() -> AppException.notFound("No account with id " + card.accountId()));

		return cards.save(new CardEntity(settlesFrom, card)).toDomain();
	}
}
