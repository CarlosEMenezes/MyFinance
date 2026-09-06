package ie.budgetTracker.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ie.budgetTracker.application.identity.UserChanges;
import ie.budgetTracker.application.identity.UserService;
import ie.budgetTracker.application.identity.dto.UpdateUserRequest;
import ie.budgetTracker.application.identity.dto.UserResponse;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The wire shape of `/users/me`, against the frozen contract. */
@WebMvcTest(UserController.class)
class UserControllerTest extends ie.budgetTracker.api.WebSliceTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UserService users;

	private static User carlos() {
		return new User(UUID.randomUUID(), "Carlos Eduardo", 24, "Freelance designer", "Ireland",
				PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY, WeekStart.MONDAY,
				new UserPreferences(true, true, false));
	}

	@Nested
	@DisplayName("reading the profile")
	class Reading {

		@Test
		void statesTheProfileAndPreferences() throws Exception {
			given(users.profile()).willReturn(UserResponse.from(carlos()));

			mvc.perform(get("/api/v1/users/me")
					.cookie(session()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Carlos Eduardo"))
					.andExpect(jsonPath("$.payCycle").value("IRREGULAR"))
					.andExpect(jsonPath("$.defaultCurrency").value("EUR"))
					.andExpect(jsonPath("$.preferences.autoConvertForeignAmounts").value(true))
					.andExpect(jsonPath("$.preferences.carryUnspentBudget").value(false));
		}

		@Test
		void writesTheDateFormatTheWayTheContractSpellsIt() throws Exception {
			// A Java enum cannot hold a hyphen, and the contract says DD-MM-YYYY.
			// The translation happens once, here, rather than in every consumer.
			given(users.profile()).willReturn(UserResponse.from(carlos()));

			mvc.perform(get("/api/v1/users/me")
					.cookie(session()))
					.andExpect(jsonPath("$.dateFormat").value("DD-MM-YYYY"));
		}

		@Test
		void leavesAnUnsetOptionalFieldNullRatherThanBlank() throws Exception {
			given(users.profile()).willReturn(UserResponse.from(new User(UUID.randomUUID(), "You",
					null, null, null, PayCycle.IRREGULAR, Currency.EUR,
					DateFormatPreference.DD_MM_YYYY, WeekStart.MONDAY,
					new UserPreferences(true, true, false))));

			// Null is "not said", and an empty string would read as an answer.
			mvc.perform(get("/api/v1/users/me")
					.cookie(session()))
					.andExpect(jsonPath("$.age").doesNotExist())
					.andExpect(jsonPath("$.role").doesNotExist());
		}
	}

	@Nested
	@DisplayName("editing the profile")
	class Editing {

		@Test
		void appliesOnlyTheFieldThatWasSent() throws Exception {
			given(users.update(any(UpdateUserRequest.class))).willReturn(UserResponse.from(carlos()));

			mvc.perform(patch("/api/v1/users/me")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Ada Lovelace"}"""))
					.andExpect(status().isOk());

			ArgumentCaptor<UpdateUserRequest> request =
					ArgumentCaptor.forClass(UpdateUserRequest.class);
			org.mockito.Mockito.verify(users).update(request.capture());
			UserChanges changes = request.getValue().toChanges();

			// Everything unmentioned stays null, so the service leaves it alone.
			assertThat(changes.name()).isEqualTo("Ada Lovelace");
			assertThat(changes.age()).isNull();
			assertThat(changes.role()).isNull();
			assertThat(changes.payCycle()).isNull();
		}

		@Test
		void tellsClearingAFieldApartFromNotMentioningIt() throws Exception {
			given(users.update(any(UpdateUserRequest.class))).willReturn(UserResponse.from(carlos()));

			mvc.perform(patch("/api/v1/users/me")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"age":null}"""))
					.andExpect(status().isOk());

			ArgumentCaptor<UpdateUserRequest> request =
					ArgumentCaptor.forClass(UpdateUserRequest.class);
			org.mockito.Mockito.verify(users).update(request.capture());
			UserChanges changes = request.getValue().toChanges();

			// An explicit null means remove it, and arrives as an empty Optional -
			// not as the null that means "untouched".
			assertThat(changes.age()).isEmpty();
		}

		@Test
		void refusesAnImplausibleAge() throws Exception {
			mvc.perform(patch("/api/v1/users/me")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"age":900}"""))
					.andExpect(status().isBadRequest());
		}

		@Test
		void acceptsTheDateFormatSpelledWithHyphens() throws Exception {
			given(users.update(any(UpdateUserRequest.class))).willReturn(UserResponse.from(carlos()));

			mvc.perform(patch("/api/v1/users/me")
					.cookie(session())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"dateFormat":"YYYY-MM-DD"}"""))
					.andExpect(status().isOk());

			ArgumentCaptor<UpdateUserRequest> request =
					ArgumentCaptor.forClass(UpdateUserRequest.class);
			org.mockito.Mockito.verify(users).update(request.capture());
			UserChanges changes = request.getValue().toChanges();

			assertThat(changes.dateFormat()).isEqualTo(DateFormatPreference.YYYY_MM_DD);
		}
	}
}
