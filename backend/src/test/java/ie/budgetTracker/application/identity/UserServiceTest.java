package ie.budgetTracker.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.identity.dto.UpdateUserRequest;
import ie.budgetTracker.domain.identity.DateFormatPreference;
import ie.budgetTracker.domain.identity.PayCycle;
import ie.budgetTracker.domain.identity.User;
import ie.budgetTracker.domain.identity.UserPreferences;
import ie.budgetTracker.domain.identity.WeekStart;
import ie.budgetTracker.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Editing the profile.
 *
 * The behaviour under test is not "does it save" but "does it leave alone what
 * the request never mentioned" - because Settings saves one field at a time,
 * and a service that rebuilt the whole user from a partial request would blank
 * a column on every keystroke.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final UUID USER = UUID.randomUUID();

	@Mock
	private UserRepository users;

	@Mock
	private CurrentUser currentUser;

	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(users, currentUser);
	}

	private static User carlos() {
		return new User(USER, "Carlos Eduardo", 24, "Freelance designer", "Ireland",
				PayCycle.IRREGULAR, Currency.EUR, DateFormatPreference.DD_MM_YYYY, WeekStart.MONDAY,
				new UserPreferences(true, true, false));
	}

	private User saveAndCapture(UpdateUserRequest request) {
		given(currentUser.id()).willReturn(USER);
		given(users.findById(USER)).willReturn(Optional.of(carlos()));
		given(users.save(any())).willAnswer(call -> call.getArgument(0));

		service.update(request);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		org.mockito.Mockito.verify(users).save(saved.capture());
		return saved.getValue();
	}

	@Nested
	@DisplayName("reading")
	class Reading {

		@Test
		void answersTheCurrentProfile() {
			given(currentUser.id()).willReturn(USER);
			given(users.findById(USER)).willReturn(Optional.of(carlos()));

			assertThat(service.profile().name()).isEqualTo("Carlos Eduardo");
		}

		@Test
		void saysSoWhenThereIsNoProfileRatherThanAnsweringAnEmptyOne() {
			given(currentUser.id()).willReturn(USER);
			given(users.findById(USER)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.profile())
					.isInstanceOf(AppException.class)
					.hasMessageContaining("No profile");
		}
	}

	@Nested
	@DisplayName("editing")
	class Editing {

		@Test
		void changesOnlyWhatWasMentioned() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setName("Ada Lovelace");

			User saved = saveAndCapture(request);

			assertThat(saved.name()).isEqualTo("Ada Lovelace");
			// Everything else survives untouched.
			assertThat(saved.age()).isEqualTo(24);
			assertThat(saved.role()).isEqualTo("Freelance designer");
			assertThat(saved.country()).isEqualTo("Ireland");
			assertThat(saved.payCycle()).isEqualTo(PayCycle.IRREGULAR);
			assertThat(saved.preferences().autoConvertForeignAmounts()).isTrue();
		}

		@Test
		void clearsAFieldThatWasExplicitlyEmptied() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setAge(null);

			// Distinct from "not mentioned": the setter ran, so the field was named.
			assertThat(saveAndCapture(request).age()).isNull();
		}

		@Test
		void treatsAWhitespaceOnlyRoleAsNoRole() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setRole("   ");

			assertThat(saveAndCapture(request).role()).isNull();
		}

		@Test
		void trimsARoleThatWasGiven() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setRole("  Illustrator  ");

			assertThat(saveAndCapture(request).role()).isEqualTo("Illustrator");
		}

		@Test
		void changesTheDefaultCurrencyEveryTotalIsStatedIn() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setDefaultCurrency(Currency.GBP);

			assertThat(saveAndCapture(request).defaultCurrency()).isEqualTo(Currency.GBP);
		}

		@Test
		void readsTheDateFormatSpelledWithHyphens() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setDateFormat("YYYY-MM-DD");

			assertThat(saveAndCapture(request).dateFormat())
					.isEqualTo(DateFormatPreference.YYYY_MM_DD);
		}

		@Test
		void replacesThePreferencesBlockWhenItIsSent() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setPreferences(new UpdateUserRequest.PreferencesRequest(false, false, true));

			assertThat(saveAndCapture(request).preferences())
					.isEqualTo(new UserPreferences(false, false, true));
		}

		@Test
		void changesTheWeekStartAndThePayCycle() {
			UpdateUserRequest request = new UpdateUserRequest();
			request.setWeekStart(WeekStart.SUNDAY);
			request.setPayCycle(PayCycle.WEEKLY);
			request.setCountry("Portugal");

			User saved = saveAndCapture(request);

			assertThat(saved.weekStart()).isEqualTo(WeekStart.SUNDAY);
			assertThat(saved.payCycle()).isEqualTo(PayCycle.WEEKLY);
			assertThat(saved.country()).isEqualTo("Portugal");
		}
	}
}
