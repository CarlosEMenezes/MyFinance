package ie.budgetTracker.application.accounts;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.accounts.dto.CreateAccountRequest;
import ie.budgetTracker.application.accounts.dto.CreatePocketRequest;
import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.accounts.Pocket;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BR-13 at the application layer, with the repository port mocked. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID SAVINGS = UUID.randomUUID();

	@Mock
	private AccountRepository accounts;

	@Mock
	private CurrentUser currentUser;

	/**
	 * BR-4, BR-5: the account list names the cards that settle from it. Mocked
	 * here, because which cards exist is not a question BR-13 answers.
	 */
	@Mock
	private CardService cards;

	private AccountService service;

	@BeforeEach
	void setUp() {
		service = new AccountService(accounts, cards, currentUser);
	}

	private static Account account(String name, String balance, boolean includeInTotals) {
		return new Account(UUID.randomUUID(), name, AccountKind.BANK, of(balance), Currency.EUR,
				includeInTotals, null, List.of());
	}

	@Nested
	@DisplayName("the boundary the controller calls")
	class Boundary {

		@Test
		void listsAccountsAsMinorUnitsForTheWire() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER))
					.willReturn(List.of(account("Revolut Current", "842.30", true)));

			assertThat(service.list()).singleElement()
					.satisfies(response -> assertThat(response.balance()).isEqualTo(84230));
		}

		@Test
		void readsAnIncomingBalanceFromMinorUnits() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of());
			given(accounts.create(eq(USER), any())).willAnswer(call -> call.getArgument(1));

			AccountResponse created = service.create(new CreateAccountRequest("Wallet",
					AccountKind.CASH, 12000, Currency.EUR, true, null));

			assertThat(created.balance()).isEqualTo(12000);
			assertThat(created.name()).isEqualTo("Wallet");
		}

		@Test
		void answersWithTheParentWhenAPocketIsAdded() {
			// BR-13 again, this time at the boundary: what comes back is the
			// account, and its balance has not moved.
			given(currentUser.id()).willReturn(USER);
			given(accounts.findForUser(USER, SAVINGS)).willReturn(Optional.of(new Account(SAVINGS,
					"AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR, true, null,
					List.of())));
			given(accounts.addPocket(eq(SAVINGS), eq("New bike"), any())).willReturn(
					new Account(SAVINGS, "AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR,
							true, null, List.of(new Pocket(UUID.randomUUID(), "New bike", of("100")))));

			AccountResponse parent =
					service.addPocket(SAVINGS, new CreatePocketRequest("New bike", 10000));

			assertThat(parent.id()).isEqualTo(SAVINGS);
			assertThat(parent.balance()).isEqualTo(145000);
			assertThat(parent.pockets()).singleElement()
					.satisfies(pocket -> assertThat(pocket.balance()).isEqualTo(10000));
		}
	}

	@Nested
	@DisplayName("the counted total (BR-13)")
	class CountedTotal {

		@Test
		void countsOnlyTheAccountsMarkedForTotals() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of(
					account("Wallet", "120", true),
					account("Revolut Current", "842.30", true),
					account("Old ISA", "5000", false)));

			assertThat(service.countedTotal()).isEqualTo(of("962.30"));
		}

		@Test
		void neverAddsAPocketToItsParent() {
			// The one dangerous misreading of BR-13. The savings account holds 1,450
			// and three pockets naming 1,130 of it; the total is 1,450.
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of(new Account(SAVINGS,
					"AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR, true, null,
					List.of(
							new Pocket(UUID.randomUUID(), "MacBook Air M4", of("410")),
							new Pocket(UUID.randomUUID(), "Emergency fund", of("640")),
							new Pocket(UUID.randomUUID(), "Interrail summer", of("80"))))));

			assertThat(service.countedTotal()).isEqualTo(of("1450.00"));
		}

		@Test
		void isZeroWhenThereAreNoAccounts() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of());

			assertThat(service.countedTotal()).isEqualTo(of("0.00"));
		}
	}

	@Nested
	@DisplayName("creating an account")
	class Creating {

		@Test
		void trimsTheNameBeforeStoringIt() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of());
			given(accounts.create(eq(USER), any())).willAnswer(call -> call.getArgument(1));

			service.create("  Wallet  ", AccountKind.CASH, of("120"), Currency.EUR, true, null);

			ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
			org.mockito.Mockito.verify(accounts).create(eq(USER), created.capture());
			assertThat(created.getValue().name()).isEqualTo("Wallet");
		}

		@Test
		void refusesADuplicateNameWhateverTheCase() {
			// Two accounts called "Wallet" and "wallet" are the same account to a
			// person, and telling them apart on a screen would be guesswork.
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of(account("Wallet", "120", true)));

			assertThatThrownBy(() -> service.create("wallet", AccountKind.CASH, of("0"),
					Currency.EUR, true, null))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("already exists");
		}

		@Test
		void refusesANamelessAccount() {
			assertThatThrownBy(() -> service.create("   ", AccountKind.CASH, of("0"),
					Currency.EUR, true, null))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("name");
		}

		@Test
		void bringsTheBalanceToScaleTwo() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findAllForUser(USER)).willReturn(List.of());
			given(accounts.create(eq(USER), any())).willAnswer(call -> call.getArgument(1));

			service.create("Jar", AccountKind.CASH, new BigDecimal("120.005"), Currency.EUR, true,
					null);

			ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
			org.mockito.Mockito.verify(accounts).create(eq(USER), created.capture());
			assertThat(created.getValue().balance()).isEqualTo(of("120.01"));
		}
	}

	@Nested
	@DisplayName("adding a pocket (BR-13)")
	class AddingAPocket {

		@Test
		void refusesToAddOneToAnAccountThatIsNotThere() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findForUser(USER, SAVINGS)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.addPocket(SAVINGS, "New bike", of("100")))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("No account");
		}

		@Test
		void refusesADuplicatePocketNameWithinTheSameAccount() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findForUser(USER, SAVINGS)).willReturn(Optional.of(new Account(SAVINGS,
					"AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR, true, null,
					List.of(new Pocket(UUID.randomUUID(), "Emergency fund", of("640"))))));

			assertThatThrownBy(() -> service.addPocket(SAVINGS, "emergency fund", of("100")))
					.isInstanceOf(AppException.class)
					.hasMessageContaining("already has a pocket");
		}

		@Test
		void writesThePocketAgainstItsParentAndNothingElse() {
			given(currentUser.id()).willReturn(USER);
			given(accounts.findForUser(USER, SAVINGS)).willReturn(Optional.of(new Account(SAVINGS,
					"AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR, true, null,
					List.of())));
			given(accounts.addPocket(eq(SAVINGS), eq("New bike"), any())).willReturn(
					new Account(SAVINGS, "AIB Savings", AccountKind.SAVINGS, of("1450"), Currency.EUR,
							true, null, List.of(new Pocket(UUID.randomUUID(), "New bike", of("100")))));

			Account parent = service.addPocket(SAVINGS, "New bike", of("100"));

			// The parent balance is untouched: the pocket names money already in it.
			assertThat(parent.balance()).isEqualTo(of("1450.00"));
			assertThat(parent.pockets()).hasSize(1);
		}
	}
}
