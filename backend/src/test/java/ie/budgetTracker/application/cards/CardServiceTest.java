package ie.budgetTracker.application.cards;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.cards.dto.CardResponse;
import ie.budgetTracker.application.cards.dto.CreateCardRequest;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CardKind;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cards as the API states them (BR-4, BR-5), with the ports mocked.
 *
 * The three cycle dates are asserted here rather than left to a screen: ADR-7
 * puts every persisted figure on this side, and a card cycle is persisted.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

	/** The date the prototype hard-codes as "today", so the fixtures line up. */
	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID REVOLUT = UUID.randomUUID();
	private static final UUID VISA = UUID.randomUUID();

	@Mock
	private CardRepository cards;

	@Mock
	private AccountRepository accounts;

	private CardService service;

	@BeforeEach
	void setUp() {
		service = new CardService(cards, accounts, () -> ADA, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void revolutExists() {
		given(accounts.findAllForUser(ADA)).willReturn(List.of(new Account(REVOLUT,
				"Revolut Current", AccountKind.BANK, of("842.30"), Currency.EUR, true, null,
				List.of())));
	}

	private static CreditCard visa() {
		return new CreditCard(VISA, "Visa 4417", REVOLUT, of("2000.00"), of("386.40"),
				new StatementCycle(25, 5));
	}

	@Nested
	@DisplayName("listing")
	class Listing {

		@Test
		@DisplayName("BR-4: states the three cycle dates the card screen shows")
		void statesTheThreeCycleDates() {
			revolutExists();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa()));

			CardResponse card = service.list().get(0);

			// The same three dates the Cards page has been rendering from its
			// fixtures. Computed here, never by the screen (ADR-7).
			assertThat(card.cycle().nextBillDate()).isEqualTo(LocalDate.parse("2026-09-05"));
			assertThat(card.cycle().billDateOnClosingDay()).isEqualTo(LocalDate.parse("2026-09-05"));
			assertThat(card.cycle().billDateAfterClosingDay())
					.isEqualTo(LocalDate.parse("2026-10-05"));
		}

		@Test
		@DisplayName("BR-5: a debit card is answered with no cycle at all")
		void aDebitCardHasNoCycle() {
			revolutExists();
			given(cards.findAllForUser(ADA))
					.willReturn(List.of(new DebitCard(UUID.randomUUID(), "Revolut debit", REVOLUT)));

			CardResponse card = service.list().get(0);

			// Null rather than zeroes: a debit card does not have a closing day
			// whose value happens to be nothing.
			assertThat(card.cycle()).isNull();
			assertThat(card.closingDay()).isNull();
			assertThat(card.dueDay()).isNull();
			assertThat(card.creditLimit()).isNull();
			assertThat(card.currentBalance()).isNull();
		}

		@Test
		@DisplayName("states money in minor units")
		void statesMoneyInMinorUnits() {
			revolutExists();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa()));

			CardResponse card = service.list().get(0);

			assertThat(card.creditLimit()).isEqualTo(200000L);
			assertThat(card.currentBalance()).isEqualTo(38640L);
		}

		@Test
		@DisplayName("names the account a card settles from, so a list needs no second request")
		void namesTheAccountItSettlesFrom() {
			revolutExists();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa()));

			assertThat(service.list().get(0).settlesFrom()).isEqualTo("Revolut Current");
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		@DisplayName("BR-4: creates a credit card with the cycle it was given")
		void createsACreditCard() {
			revolutExists();
			given(cards.create(eq(ADA), any())).willReturn(visa());

			service.create(CreateCardRequest.credit("Visa 4417", REVOLUT, 200000L, 25, 5));

			ArgumentCaptor<Card> written = ArgumentCaptor.forClass(Card.class);
			Mockito.verify(cards).create(eq(ADA), written.capture());
			assertThat(written.getValue()).isInstanceOfSatisfying(CreditCard.class,
					card -> assertThat(card.cycle()).isEqualTo(new StatementCycle(25, 5)));
		}

		@Test
		@DisplayName("BR-1: a new credit card starts owing nothing")
		void aNewCreditCardStartsAtZero() {
			revolutExists();
			given(cards.create(eq(ADA), any())).willReturn(visa());

			service.create(CreateCardRequest.credit("Visa 4417", REVOLUT, 200000L, 25, 5));

			ArgumentCaptor<Card> written = ArgumentCaptor.forClass(Card.class);
			Mockito.verify(cards).create(eq(ADA), written.capture());
			// The opening balance is not a field on the request: what is owed comes
			// from transactions, and letting someone type it would put a figure into
			// BR-1 that nothing accounts for.
			assertThat(((CreditCard) written.getValue()).currentBalance())
					.isEqualByComparingTo(of("0.00"));
		}

		@Test
		@DisplayName("BR-5: creates a debit card with no cycle to give it")
		void createsADebitCard() {
			revolutExists();
			given(cards.create(eq(ADA), any()))
					.willReturn(new DebitCard(UUID.randomUUID(), "Revolut debit", REVOLUT));

			CardResponse created = service.create(CreateCardRequest.debit("Revolut debit", REVOLUT));

			assertThat(created.kind()).isEqualTo(CardKind.DEBIT);
			assertThat(created.cycle()).isNull();
		}

		@Test
		@DisplayName("BR-4: refuses a credit card with no closing day, naming the field")
		void refusesACreditCardWithoutItsCycle() {
			assertThatThrownBy(() -> service.create(new CreateCardRequest(CardKind.CREDIT,
					"Visa 4417", REVOLUT, 200000L, null, 5)))
					.isInstanceOf(AppException.class)
					.satisfies(refused -> {
						// A 400 that says only "invalid" leaves the form guessing which
						// field it was (spec §4).
						assertThat(((AppException) refused).kind())
								.isEqualTo(AppException.Kind.INVALID);
						assertThat(((AppException) refused).field()).isEqualTo("closingDay");
					});
		}

		@Test
		@DisplayName("BR-4: refuses a credit card with no limit, naming the field")
		void refusesACreditCardWithoutALimit() {
			assertThatThrownBy(() -> service.create(new CreateCardRequest(CardKind.CREDIT,
					"Visa 4417", REVOLUT, null, 25, 5)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("creditLimit");
		}

		@Test
		@DisplayName("BR-5: refuses a debit card that was sent a statement cycle")
		void refusesADebitCardCarryingACycle() {
			// A debit card has no cycle, so a closing day on one is not a harmless
			// extra field. It is a request that means something impossible, and
			// accepting it quietly would leave the sender believing in a cycle that
			// does not exist.
			assertThatThrownBy(() -> service.create(new CreateCardRequest(CardKind.DEBIT,
					"Revolut debit", REVOLUT, null, 25, null)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("closingDay");
		}

		@Test
		@DisplayName("refuses a name this user already has on another card")
		void refusesADuplicateName() {
			revolutExists();
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa()));

			assertThatThrownBy(() -> service.create(CreateCardRequest.debit("visa 4417", REVOLUT)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.CONFLICT);
		}

		@Test
		@DisplayName("another account holder id is not found rather than forbidden")
		void anotherAccountHolderIdIsNotFound() {
			given(accounts.findAllForUser(ADA)).willReturn(List.of());

			// ADR-11: a 403 would confirm the id exists.
			assertThatThrownBy(() -> service.create(CreateCardRequest.debit("Sneaky", REVOLUT)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).kind())
					.isEqualTo(AppException.Kind.NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("naming the cards that settle from each account")
	class CardNamesByAccount {

		@Test
		@DisplayName("BR-13: the Accounts page is told which cards settle from each account")
		void groupsCardNamesByAccount() {
			given(cards.findAllForUser(ADA)).willReturn(List.of(visa(),
					new DebitCard(UUID.randomUUID(), "Revolut debit", REVOLUT)));

			assertThat(service.cardNamesByAccount())
					.containsEntry(REVOLUT, List.of("Visa 4417", "Revolut debit"));
		}
	}
}
