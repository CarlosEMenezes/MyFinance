package ie.budgetTracker.api.accounts;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountService;
import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.accounts.dto.CreateAccountRequest;
import ie.budgetTracker.application.accounts.dto.CreatePocketRequest;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.accounts.Pocket;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of `/accounts`, against the contract
 * frontend/src/types/api.ts already froze.
 *
 * Every field name and every unit here is one the frontend is already written
 * to read. A rename that looked harmless on this side would break a page that
 * has been passing its own tests for weeks.
 */
@WebMvcTest(AccountController.class)
class AccountControllerTest extends ie.budgetTracker.api.WebSliceTest {

	private static final UUID SAVINGS_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private AccountService accounts;

	private static AccountResponse savings() {
		return AccountResponse.from(new Account(SAVINGS_ID, "AIB Savings", AccountKind.SAVINGS,
				of("1450.00"), Currency.EUR, true, "ring-fenced for goals",
				List.of(new Pocket(UUID.randomUUID(), "MacBook Air M4", of("410.00")))));
	}

	@Nested
	@DisplayName("listing")
	class Listing {

		@Test
		void statesMoneyInMinorUnits() throws Exception {
			given(accounts.list()).willReturn(List.of(savings()));

			mvc.perform(get("/api/v1/accounts")
					.cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].name").value("AIB Savings"))
					.andExpect(jsonPath("$[0].kind").value("SAVINGS"))
					// 1450.00 crosses as 145000, not as 1450.0.
					.andExpect(jsonPath("$[0].balance").value(145000))
					.andExpect(jsonPath("$[0].currency").value("EUR"))
					.andExpect(jsonPath("$[0].includeInTotals").value(true));
		}

		@Test
		void namesPocketsWithoutTotallingThem() throws Exception {
			given(accounts.list()).willReturn(List.of(savings()));

			// BR-13: the payload carries the account balance and the pockets inside
			// it, and no figure that is the two added together.
			mvc.perform(get("/api/v1/accounts")
					.cookie(session()))
					.andExpect(jsonPath("$[0].balance").value(145000))
					.andExpect(jsonPath("$[0].pockets[0].name").value("MacBook Air M4"))
					.andExpect(jsonPath("$[0].pockets[0].balance").value(41000))
					.andExpect(jsonPath("$[0].pockets[0].accountId").value(SAVINGS_ID.toString()));
		}

		@Test
		void answersAnEmptyListRatherThanNothing() throws Exception {
			given(accounts.list()).willReturn(List.of());

			mvc.perform(get("/api/v1/accounts")
					.cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").isArray())
					.andExpect(jsonPath("$").isEmpty());
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		void createsAnAccountAndAnswers201() throws Exception {
			given(accounts.create(any(CreateAccountRequest.class)))
					.willReturn(AccountResponse.from(new Account(UUID.randomUUID(), "Wallet",
							AccountKind.CASH, of("120.00"), Currency.EUR, true, null, List.of())));

			mvc.perform(post("/api/v1/accounts")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Wallet","kind":"CASH","balance":12000,"currency":"EUR",
							 "includeInTotals":true}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.balance").value(12000));
		}

		@Test
		void refusesANamelessAccountWithAFieldLevelReason() throws Exception {
			// Spec §4: a 400 carries the list of what was wrong, because a form
			// told only "invalid" has to guess which field it was.
			mvc.perform(post("/api/v1/accounts")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"","kind":"CASH","balance":0,"currency":"EUR","includeInTotals":true}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").exists())
					.andExpect(jsonPath("$.errors[0].field").value("name"));
		}

		@Test
		void answers409WithTheReasonWhenTheNameIsTaken() throws Exception {
			willThrow(AppException.conflict("An account called Wallet already exists"))
					.given(accounts).create(any(CreateAccountRequest.class));

			mvc.perform(post("/api/v1/accounts")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Wallet","kind":"CASH","balance":0,"currency":"EUR","includeInTotals":true}"""))
					.andExpect(status().isConflict())
					// The frontend renders `title` verbatim, so it has to be a sentence
					// worth showing rather than a code.
					.andExpect(jsonPath("$.title").value("An account called Wallet already exists"));
		}
	}

	@Nested
	@DisplayName("adding a pocket (BR-13)")
	class AddingAPocket {

		@Test
		void answersWithTheParentAccountRatherThanThePocket() throws Exception {
			given(accounts.addPocket(eq(SAVINGS_ID), any(CreatePocketRequest.class)))
					.willReturn(savings());

			// What changed is the account's composition, not its balance, and the
			// response says so by being the account.
			mvc.perform(post("/api/v1/accounts/" + SAVINGS_ID + "/pockets")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"MacBook Air M4","balance":41000}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.id").value(SAVINGS_ID.toString()))
					.andExpect(jsonPath("$.balance").value(145000))
					.andExpect(jsonPath("$.pockets[0].balance").value(41000));
		}

		@Test
		void answers404ForAnAccountThatIsNotThere() throws Exception {
			willThrow(AppException.notFound("No account with id " + SAVINGS_ID))
					.given(accounts).addPocket(any(), any(CreatePocketRequest.class));

			mvc.perform(post("/api/v1/accounts/" + SAVINGS_ID + "/pockets")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"MacBook Air M4","balance":41000}"""))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.title").value("No account with id " + SAVINGS_ID));
		}

		@Test
		void refusesANegativePocketBalance() throws Exception {
			mvc.perform(post("/api/v1/accounts/" + SAVINGS_ID + "/pockets")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"MacBook Air M4","balance":-100}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("balance"));
		}
	}
}
