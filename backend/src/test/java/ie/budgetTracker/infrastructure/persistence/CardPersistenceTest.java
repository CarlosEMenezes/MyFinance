package ie.budgetTracker.infrastructure.persistence;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.auth.CredentialsRepository;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The persistence side of BR-4 and BR-5, against the real schema on H2.
 *
 * The migrations run rather than Hibernate generating a schema, so what is
 * tested is the table the application will actually meet - including the check
 * constraint that is BR-5's last line of defence.
 */
@DataJpaTest
@Import({ JpaCardRepository.class, JpaAccountRepository.class, JpaCredentialsRepository.class,
		CardPersistenceTest.Time.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CardPersistenceTest {

	@TestConfiguration
	static class Time {
		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}
	}

	@Autowired
	private CardRepository cards;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private CredentialsRepository credentials;

	@Autowired
	private EntityManager entityManager;

	private UUID owner;
	private UUID settlesFrom;

	@BeforeEach
	void registerAnOwnerWithAnAccount() {
		owner = register("owner@example.com");
		settlesFrom = accountFor(owner, "Revolut Current");
	}

	private UUID register(String email) {
		return credentials.register(email, "{noop}irrelevant", new User(null, "Someone", null, null,
				null, PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY,
				WeekStart.MONDAY, new UserPreferences(true, true, false))).id();
	}

	private UUID accountFor(UUID user, String name) {
		return accounts.create(user, new Account(null, name, AccountKind.BANK, of("842.30"),
				Currency.EUR, true, null, List.of())).id();
	}

	@Test
	@DisplayName("BR-4: stores a credit card with its cycle and reads it back")
	void storesACreditCardWithItsCycle() {
		cards.create(owner, new CreditCard(null, "Visa 4417", settlesFrom, of("2000.00"),
				of("386.40"), new StatementCycle(25, 5)));

		assertThat(cards.findAllForUser(owner)).singleElement()
				.isInstanceOfSatisfying(CreditCard.class, card -> {
					assertThat(card.name()).isEqualTo("Visa 4417");
					assertThat(card.creditLimit()).isEqualTo(of("2000.00"));
					assertThat(card.currentBalance()).isEqualTo(of("386.40"));
					assertThat(card.cycle()).isEqualTo(new StatementCycle(25, 5));
					assertThat(card.id()).isNotNull();
				});
	}

	@Test
	@DisplayName("BR-5: a debit card comes back with no cycle to ask about")
	void storesADebitCardWithNoCycle() {
		cards.create(owner, new DebitCard(null, "Revolut debit", settlesFrom));

		// Not a credit card with empty fields: a different type, with nowhere to
		// put a closing day at all.
		assertThat(cards.findAllForUser(owner)).singleElement().isInstanceOf(DebitCard.class);
	}

	@Test
	@DisplayName("BR-5: the schema refuses a debit card carrying a cycle")
	void theSchemaRefusesADebitCardCarryingACycle() {
		// The sealed domain type makes this unwriteable through the application.
		// This is the half of BR-5 that survives a hand-written UPDATE, so it is
		// asserted by going around the mapping rather than through it.
		assertThatThrownBy(() -> {
			entityManager.createNativeQuery("""
					INSERT INTO card (id, account_id, name, kind, closing_day, due_day)
					VALUES (?1, ?2, 'Smuggled', 'DEBIT', 25, 5)""")
					.setParameter(1, UUID.randomUUID())
					.setParameter(2, settlesFrom)
					.executeUpdate();
			entityManager.flush();
		}).hasStackTraceContaining("CARD_CARRIES_A_CYCLE_ONLY_WHEN_IT_IS_CREDIT");
	}

	@Test
	@DisplayName("BR-4: the schema refuses a cycle day that is not in every month")
	void theSchemaRefusesACycleDayOutsideTheRange() {
		assertThatThrownBy(() -> {
			entityManager.createNativeQuery("""
					INSERT INTO card (id, account_id, name, kind, credit_limit, current_balance,
					                  closing_day, due_day)
					VALUES (?1, ?2, 'Impossible', 'CREDIT', 2000.00, 0.00, 31, 5)""")
					.setParameter(1, UUID.randomUUID())
					.setParameter(2, settlesFrom)
					.executeUpdate();
			entityManager.flush();
		}).hasStackTraceContaining("CARD_CYCLE_DAYS_EXIST_IN_EVERY_MONTH");
	}

	@Test
	@DisplayName("keeps cards belonging to another user out of the list")
	void isolatesUsers() {
		cards.create(owner, new DebitCard(null, "Revolut debit", settlesFrom));
		UUID someoneElse = register("someone-else@example.com");

		// ADR-11: a card has no owner column, so the join through the account is
		// the only thing separating two people's cards. It is asserted against a
		// real second user rather than a made-up id.
		assertThat(cards.findAllForUser(someoneElse)).isEmpty();
	}

	@Test
	@DisplayName("finds one card for its owner and nobody else")
	void findsOneForItsOwner() {
		Card debit = cards.create(owner, new DebitCard(null, "Revolut debit", settlesFrom));

		assertThat(cards.findForUser(owner, debit.id())).isPresent();
		assertThat(cards.findForUser(register("nosy@example.com"), debit.id())).isEmpty();
	}

	@Test
	@DisplayName("will not hang a card off another user's account")
	void willNotHangACardOffAnotherUsersAccount() {
		UUID intruder = register("intruder@example.com");

		assertThatThrownBy(() -> cards.create(intruder, new DebitCard(null, "Sneaky", settlesFrom)))
				.isInstanceOf(AppException.class)
				.extracting(refused -> ((AppException) refused).kind())
				.isEqualTo(AppException.Kind.NOT_FOUND);
	}

	@Test
	@DisplayName("orders cards by name so a list does not reshuffle itself")
	void ordersCardsByName() {
		cards.create(owner, new DebitCard(null, "Revolut debit", settlesFrom));
		cards.create(owner, new DebitCard(null, "AIB debit", settlesFrom));

		assertThat(cards.findAllForUser(owner)).extracting(Card::name)
				.containsExactly("AIB debit", "Revolut debit");
	}
}
