package ie.budgetTracker.api.cards;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.cards.dto.CardResponse;
import ie.budgetTracker.application.cards.dto.CreateCardRequest;
import ie.budgetTracker.domain.cards.CardCycleDates;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of /cards, against the contract frontend/src/types/api.ts
 * already froze.
 *
 * Every field name and every unit here is one the Cards page is already written
 * to read. A rename that looked harmless on this side would break a page that
 * has been passing its own tests for weeks (ADR-12).
 */
@WebMvcTest(CardController.class)
class CardControllerTest extends ie.budgetTracker.api.WebSliceTest {

	private static final UUID VISA = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID REVOLUT = UUID.fromString("33333333-3333-4333-8333-333333333333");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CardService cards;

	private static CardResponse visa() {
		return CardResponse.from(
				new CreditCard(VISA, "Visa 4417", REVOLUT, of("2000.00"), of("386.40"),
						new StatementCycle(25, 5)),
				"Revolut Current",
				new CardCycleDates(LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-05"),
						LocalDate.parse("2026-10-05")));
	}

	private static CardResponse revolutDebit() {
		return CardResponse.from(new DebitCard(UUID.randomUUID(), "Revolut debit", REVOLUT),
				"Revolut Current", null);
	}

	@Nested
	@DisplayName("listing")
	class Listing {

		@Test
		void statesMoneyInMinorUnitsAndDatesAsIsoDays() throws Exception {
			given(cards.list()).willReturn(List.of(visa()));

			mvc.perform(get("/api/v1/cards").cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].name").value("Visa 4417"))
					.andExpect(jsonPath("$[0].kind").value("CREDIT"))
					.andExpect(jsonPath("$[0].settlesFrom").value("Revolut Current"))
					// 2000.00 crosses as 200000, not 2000.0.
					.andExpect(jsonPath("$[0].creditLimit").value(200000))
					.andExpect(jsonPath("$[0].currentBalance").value(38640))
					.andExpect(jsonPath("$[0].closingDay").value(25))
					.andExpect(jsonPath("$[0].dueDay").value(5))
					// ISO days, not an array of numbers and not a timestamp.
					.andExpect(jsonPath("$[0].cycle.nextBillDate").value("2026-09-05"))
					.andExpect(jsonPath("$[0].cycle.billDateOnClosingDay").value("2026-09-05"))
					.andExpect(jsonPath("$[0].cycle.billDateAfterClosingDay").value("2026-10-05"));
		}

		@Test
		@DisplayName("BR-5: a debit card carries nulls where a cycle would be")
		void aDebitCardCarriesNullsWhereACycleWouldBe() throws Exception {
			given(cards.list()).willReturn(List.of(revolutDebit()));

			// The page reads `cycle === null` to decide which card it is drawing, so
			// these have to be absent rather than zero.
			mvc.perform(get("/api/v1/cards").cookie(session()))
					.andExpect(jsonPath("$[0].kind").value("DEBIT"))
					.andExpect(jsonPath("$[0].cycle").doesNotExist())
					.andExpect(jsonPath("$[0].closingDay").doesNotExist())
					.andExpect(jsonPath("$[0].creditLimit").doesNotExist());
		}

		@Test
		void answersAnEmptyListRatherThanNothing() throws Exception {
			given(cards.list()).willReturn(List.of());

			mvc.perform(get("/api/v1/cards").cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").isArray())
					.andExpect(jsonPath("$").isEmpty());
		}

		@Test
		void refusesToAnswerWithoutASession() throws Exception {
			// The slice runs the real security chain, so a controller that forgot to
			// be protected shows up here rather than in production.
			mvc.perform(get("/api/v1/cards")).andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("creating")
	class Creating {

		@Test
		void createsACreditCardAndAnswers201() throws Exception {
			given(cards.create(any(CreateCardRequest.class))).willReturn(visa());

			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"CREDIT","name":"Visa 4417","accountId":"%s",
							 "creditLimit":200000,"closingDay":25,"dueDay":5}"""
							.formatted(REVOLUT)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.cycle.nextBillDate").value("2026-09-05"));
		}

		@Test
		void createsADebitCardFromABodyWithNoCycleInIt() throws Exception {
			given(cards.create(any(CreateCardRequest.class))).willReturn(revolutDebit());

			// BR-5: the frontend sends a discriminated union, and the DEBIT arm has
			// no cycle fields to send. The API has to accept exactly that body.
			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"DEBIT","name":"Revolut debit","accountId":"%s"}"""
							.formatted(REVOLUT)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.cycle").doesNotExist());
		}

		@Test
		void refusesANamelessCardWithAFieldLevelReason() throws Exception {
			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"DEBIT","name":"","accountId":"%s"}""".formatted(REVOLUT)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").exists())
					.andExpect(jsonPath("$.errors[0].field").value("name"));
		}

		@Test
		@DisplayName("BR-4: refuses a cycle day that does not exist in every month")
		void refusesACycleDayOutsideTheRange() throws Exception {
			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"CREDIT","name":"Visa 4417","accountId":"%s",
							 "creditLimit":200000,"closingDay":31,"dueDay":5}"""
							.formatted(REVOLUT)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("closingDay"));
		}

		@Test
		@DisplayName("BR-4: a missing cycle is a 400 that names the field")
		void aMissingCycleIsA400ThatNamesTheField() throws Exception {
			willThrow(AppException.invalid("closingDay",
					"A credit card needs the day its statement closes (BR-4)"))
					.given(cards).create(any(CreateCardRequest.class));

			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"CREDIT","name":"Visa 4417","accountId":"%s",
							 "creditLimit":200000,"dueDay":5}""".formatted(REVOLUT)))
					.andExpect(status().isBadRequest())
					// The frontend renders `title` verbatim, so it has to be a sentence
					// worth showing rather than a code.
					.andExpect(jsonPath("$.title")
							.value("A credit card needs the day its statement closes (BR-4)"))
					.andExpect(jsonPath("$.errors[0].field").value("closingDay"));
		}

		@Test
		void answers409WithTheReasonWhenTheNameIsTaken() throws Exception {
			willThrow(AppException.conflict("A card called Visa 4417 already exists"))
					.given(cards).create(any(CreateCardRequest.class));

			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"DEBIT","name":"Visa 4417","accountId":"%s"}"""
							.formatted(REVOLUT)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("A card called Visa 4417 already exists"));
		}

		@Test
		@DisplayName("an account that is not this user's answers 404, never 403")
		void anUnknownAccountAnswers404() throws Exception {
			willThrow(AppException.notFound("No account with id " + REVOLUT))
					.given(cards).create(any(CreateCardRequest.class));

			// ADR-11: a 403 would confirm the id exists, which is exactly what
			// somebody enumerating ids wants to learn.
			mvc.perform(post("/api/v1/cards")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"kind":"DEBIT","name":"Sneaky","accountId":"%s"}"""
							.formatted(REVOLUT)))
					.andExpect(status().isNotFound());
		}
	}
}
