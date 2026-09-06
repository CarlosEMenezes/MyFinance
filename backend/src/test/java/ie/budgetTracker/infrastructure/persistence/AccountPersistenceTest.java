package ie.budgetTracker.infrastructure.persistence;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The persistence side of BR-13, against the real schema on H2.
 *
 * These run the Flyway migrations rather than letting Hibernate generate a
 * schema, so what is tested is the table the application will actually meet.
 */
@DataJpaTest
@Import({ JpaAccountRepository.class, JpaUserRepository.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class AccountPersistenceTest {

	/** The row V3__seed_single_user.sql inserts. */
	private static final UUID SEEDED_USER = UUID.fromString("00000000-0000-4000-8000-000000000001");

	@Autowired
	private AccountRepository accounts;

	private Account newAccount(String name, String balance, boolean includeInTotals) {
		return accounts.create(SEEDED_USER, new Account(null, name, AccountKind.BANK,
				of(balance), Currency.EUR, includeInTotals, null, List.of()));
	}

	@Test
	@DisplayName("stores an account and reads it back at scale 2")
	void storesAnAccount() {
		newAccount("Revolut Current", "842.30", true);

		assertThat(accounts.findAllForUser(SEEDED_USER))
				.singleElement()
				.satisfies(account -> {
					assertThat(account.name()).isEqualTo("Revolut Current");
					assertThat(account.balance()).isEqualTo(of("842.30"));
					assertThat(account.id()).isNotNull();
				});
	}

	@Test
	@DisplayName("keeps accounts belonging to another user out of the list")
	void isolatesUsers() {
		newAccount("Revolut Current", "842.30", true);

		assertThat(accounts.findAllForUser(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("BR-13: adding a pocket does not move the parent balance")
	void addingAPocketLeavesTheParentBalanceAlone() {
		Account savings = newAccount("AIB Savings", "1450.00", true);

		Account withPocket = accounts.addPocket(savings.id(), "MacBook Air M4", of("410"));

		// The pocket names part of the 1,450 that is already there. A schema or a
		// mapping that added to the parent would count the same euro twice.
		assertThat(withPocket.balance()).isEqualTo(of("1450.00"));
		assertThat(withPocket.pockets()).singleElement()
				.satisfies(pocket -> assertThat(pocket.balance()).isEqualTo(of("410.00")));
	}

	@Test
	@DisplayName("BR-13: several pockets still leave the parent balance alone")
	void severalPocketsStillLeaveTheParentAlone() {
		Account savings = newAccount("AIB Savings", "1450.00", true);

		accounts.addPocket(savings.id(), "MacBook Air M4", of("410"));
		accounts.addPocket(savings.id(), "Emergency fund", of("640"));
		Account withPockets = accounts.addPocket(savings.id(), "Interrail summer", of("80"));

		assertThat(withPockets.balance()).isEqualTo(of("1450.00"));
		assertThat(withPockets.pockets()).hasSize(3);
	}

	@Test
	@DisplayName("finds one account for its owner and nobody else")
	void findsOneForItsOwner() {
		Account wallet = newAccount("Wallet", "120", true);

		assertThat(accounts.findForUser(SEEDED_USER, wallet.id())).isPresent();
		assertThat(accounts.findForUser(UUID.randomUUID(), wallet.id())).isEmpty();
	}

	@Test
	@DisplayName("remembers an account kept out of totals")
	void remembersAnExcludedAccount() {
		newAccount("Old ISA", "5000", false);

		assertThat(accounts.findAllForUser(SEEDED_USER)).singleElement()
				.satisfies(account -> assertThat(account.includeInTotals()).isFalse());
	}
}
