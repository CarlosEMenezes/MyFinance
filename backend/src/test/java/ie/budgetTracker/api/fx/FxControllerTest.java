package ie.budgetTracker.api.fx;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.fx.FxService;
import ie.budgetTracker.application.fx.dto.FxRatesResponse;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The wire shape of `GET /fx/rates`, which Settings reads (BR-8). */
@WebMvcTest(FxController.class)
class FxControllerTest extends ie.budgetTracker.api.WebSliceTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private FxService fx;

	@Test
	@DisplayName("BR-8: states the base, the rates and when they were pulled")
	void statesTheRatesAndTheirAge() throws Exception {
		given(fx.rates()).willReturn(new FxRatesResponse(Currency.EUR, Map.of(
				Currency.EUR, BigDecimal.ONE,
				Currency.USD, new BigDecimal("1.0858")),
				Instant.parse("2026-08-31T07:12:00Z")));

		// The age is part of the answer: a converted total shown without it
		// invites more trust than it has earned.
		mvc.perform(get("/api/v1/fx/rates").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.base").value("EUR"))
				.andExpect(jsonPath("$.rates.USD").value(1.0858))
				.andExpect(jsonPath("$.fetchedAt").value("2026-08-31T07:12:00Z"));
	}

	@Test
	@DisplayName("BR-8: answers 503 when no rates can be had, rather than a table of ones")
	void answers503WhenNoRatesCanBeHad() throws Exception {
		willThrow(AppException.unavailable("Exchange rates could not be fetched"))
				.given(fx).rates();

		mvc.perform(get("/api/v1/fx/rates").cookie(session()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Exchange rates could not be fetched"));
	}

	@Test
	void refusesToAnswerWithoutASession() throws Exception {
		mvc.perform(get("/api/v1/fx/rates")).andExpect(status().isUnauthorized());
	}
}
