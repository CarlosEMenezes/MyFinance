package ie.budgetTracker.api.transactions;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.transactions.TransactionService;
import ie.budgetTracker.application.transactions.dto.CreateTransactionRequest;
import ie.budgetTracker.application.transactions.dto.TransactionResponse;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.transactions.PaymentMethod;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * The wire shape of `POST /transactions`, against the frozen contract
 * (ADR-12).
 *
 * The log form sends what was typed and reads back what the server made of it,
 * so the three BR-8 figures and BR-4's date all have to be on the answer.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest extends ie.budgetTracker.api.WebSliceTest {

	private static final UUID GROCERIES = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final UUID VISA = UUID.fromString("66666666-6666-4666-8666-666666666666");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private TransactionService transactions;

	/** The application's own mapper, so a replay is byte-for-byte what it wrote. */
	@Autowired
	private ObjectMapper json;

	private static final String BODY = """
			{"type":"EXPENSE","categoryId":"%s","amount":10000,"currency":"USD",
			 "date":"2026-08-20","paymentMethodId":"%s"}""".formatted(GROCERIES, VISA);

	private static TransactionResponse logged() {
		return TransactionResponse.from(new Transaction(
				UUID.fromString("77777777-7777-4777-8777-777777777777"),
				TransactionType.EXPENSE, GROCERIES, of("100.00"), Currency.USD, of("92.10"),
				new BigDecimal("0.92098"), LocalDate.parse("2026-08-20"),
				new PaymentMethod(VISA, PaymentMethodKind.CREDIT_CARD), null, null, null,
				LocalDate.parse("2026-09-05")));
	}

	@Test
	@DisplayName("BR-8, BR-4: answers with the converted amount, the rate and the bill date")
	void answersWithEverythingTheServerWorkedOut() throws Exception {
		given(transactions.log(any(CreateTransactionRequest.class))).willReturn(logged());

		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(10000))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.amountInDefaultCurrency").value(9210))
				.andExpect(jsonPath("$.fxRate").value(0.92098))
				.andExpect(jsonPath("$.paymentMethodId").value(VISA.toString()))
				// BR-4: not the purchase date.
				.andExpect(jsonPath("$.plannedExpenseDate").value("2026-09-05"));
	}

	@Test
	@DisplayName("BR-8: a rate that cannot be had answers 503 with a sentence, not a figure")
	void aMissingRateAnswers503() throws Exception {
		willThrow(AppException.unavailable(
				"No exchange rate from BRL to EUR is available. The entry has not been saved."))
				.given(transactions).log(any(CreateTransactionRequest.class));

		// 503 rather than 400: the request was fine, the world was not. The
		// frontend renders `title`, so it has to say what happened to the entry.
		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value(
						"No exchange rate from BRL to EUR is available. The entry has not been saved."));
	}

	@Test
	void refusesAnEntryWithNoPaymentMethod() throws Exception {
		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":10000,"currency":"EUR",
						 "date":"2026-08-20"}""".formatted(GROCERIES)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("paymentMethodId"));
	}

	@Test
	void refusesANegativeAmount() throws Exception {
		// A negative expense is a refund wearing the wrong clothes, and BR-9 would
		// colour the variance from it as though it meant something.
		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"EXPENSE","categoryId":"%s","amount":-10000,"currency":"EUR",
						 "date":"2026-08-20","paymentMethodId":"%s"}"""
						.formatted(GROCERIES, VISA)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("amount"));
	}

	@Test
	@DisplayName("spec §4: a retry with the same key is answered, not repeated")
	void aRetryWithTheSameKeyIsAnswered() throws Exception {
		given(transactions.log(any(CreateTransactionRequest.class))).willReturn(logged());
		given(idempotencyStore.replay(any(), eq("a-key"), eq("POST /transactions")))
				.willReturn(Optional.of(json.writeValueAsString(logged())));

		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.header("Idempotency-Key", "a-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amountInDefaultCurrency").value(9210));

		// The entry is not written a second time: a double tap must not log the
		// expense twice, and every total built on top would be wrong.
		Mockito.verify(transactions, Mockito.never()).log(any());
	}

	@Test
	@DisplayName("a request with no key behaves exactly as before")
	void aRequestWithNoKeyIsUnchanged() throws Exception {
		given(transactions.log(any(CreateTransactionRequest.class))).willReturn(logged());

		// The frozen contract sends no key (ADR-12), so this is the ordinary path
		// and it must stay ordinary.
		mvc.perform(post("/api/v1/transactions")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
				.andExpect(status().isCreated());

		Mockito.verifyNoInteractions(idempotencyStore);
	}

	@Test
	void refusesToLogAnythingWithoutASession() throws Exception {
		mvc.perform(post("/api/v1/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
				.andExpect(status().isUnauthorized());
	}
}
