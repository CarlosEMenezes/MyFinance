package ie.budgetTracker.infrastructure.persistence;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.auth.CredentialsRepository;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
@Import({ JpaAccountRepository.class, JpaCredentialsRepository.class,
		AccountPersistenceTest.Time.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class AccountPersistenceTest {

	@TestConfiguration
	static class Time {
		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}
	}

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private CredentialsRepository credentials;

	private UUID owner;

	@BeforeEach
	void registerAnOwner() {
		// Real users now, because V4 removed the seeded placeholder. Every account
		// belongs to somebody, and the schema will not accept one that does not.
		owner = register("owner@example.com");
	}

	private UUID register(String email) {
		return credentials.register(email, "{noop}irrelevant", new User(null, "Someone", null, null,
				null, PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY,
				WeekStart.MONDAY, new UserPreferences(true, true, false))).id();
	}

	private Account newAccount(String name, String balance, boolean includeInTotals) {
		return accounts.create(owner, new Account(null, name, AccountKind.BANK,
				of(balance), Currency.EUR, includeInTotals, null, List.of()));
	}

	@Test
	@DisplayName("stores an account and reads it back at scale 2")
	void storesAnAccount() {
		newAccount("Revolut Current", "842.30", true);

		assertThat(accounts.findAllForUser(owner))
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
		UUID someoneElse = register("someone-else@example.com");

		// ADR-11: the filter is the only thing separating two people's money, so
		// it is asserted against a real second user rather than a made-up id.
		assertThat(accounts.findAllForUser(someoneElse)).isEmpty();
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

		assertThat(accounts.findForUser(owner, wallet.id())).isPresent();
		assertThat(accounts.findForUser(register("nosy@example.com"), wallet.id())).isEmpty();
	}

	@Test
	@DisplayName("remembers an account kept out of totals")
	void remembersAnExcludedAccount() {
		newAccount("Old ISA", "5000", false);

		assertThat(accounts.findAllForUser(owner)).singleElement()
				.satisfies(account -> assertThat(account.includeInTotals()).isFalse());
	}
}
