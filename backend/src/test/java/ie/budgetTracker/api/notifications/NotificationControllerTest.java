package ie.budgetTracker.api.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.notifications.NotificationService;
import ie.budgetTracker.application.notifications.dto.MarkNotificationsReadRequest;
import ie.budgetTracker.application.notifications.dto.NotificationResponse;
import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import ie.budgetTracker.application.notifications.dto.UpdateNotificationSettingsRequest;
import ie.budgetTracker.domain.notifications.DueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The wire shape of `/notifications`, against the frozen contract (ADR-12). */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends ie.budgetTracker.api.WebSliceTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NotificationService notifications;

	private static NotificationResponse visaBill() {
		return new NotificationResponse("card-visa", "Visa 4417 card payment",
				"statement closes day 25", LocalDate.parse("2026-09-05"), 5, 38640L,
				DueSource.CARD_BILL, null);
	}

	@Test
	@DisplayName("BR-12: an item carries its urgency and its read state")
	void anItemCarriesItsUrgencyAndReadState() throws Exception {
		given(notifications.queue()).willReturn(List.of(visaBill()));

		mvc.perform(get("/api/v1/notifications").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].key").value("card-visa"))
				.andExpect(jsonPath("$[0].dueDate").value("2026-09-05"))
				// Sent rather than derived on the screen: a client computing it from
				// its own clock would disagree across time zones.
				.andExpect(jsonPath("$[0].daysUntilDue").value(5))
				.andExpect(jsonPath("$[0].amount").value(38640))
				.andExpect(jsonPath("$[0].sourceType").value("CARD_BILL"))
				.andExpect(jsonPath("$[0].readAt").doesNotExist());
	}

	@Test
	@DisplayName("BR-12: a read item says when it was read")
	void aReadItemSaysWhen() throws Exception {
		given(notifications.queue()).willReturn(List.of(new NotificationResponse("card-visa",
				"Visa 4417 card payment", "statement closes day 25",
				LocalDate.parse("2026-09-05"), 5, 38640L, DueSource.CARD_BILL,
				Instant.parse("2026-08-30T21:00:00Z"))));

		mvc.perform(get("/api/v1/notifications").cookie(session()))
				.andExpect(jsonPath("$[0].readAt").value("2026-08-30T21:00:00Z"));
	}

	@Test
	@DisplayName("BR-12: marking read answers the recomputed queue, as the contract declares")
	void markingReadAnswersTheQueue() throws Exception {
		given(notifications.queue()).willReturn(List.of(visaBill()));

		// The frozen contract types this call as returning Notification[], and a
		// promise typed as a list that resolves to undefined is a lie the
		// compiler cannot catch (ADR-12).
		mvc.perform(patch("/api/v1/notifications/read")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"keys":["card-visa","loan-1"],"read":true}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].key").value("card-visa"));

		Mockito.verify(notifications).markRead(any(MarkNotificationsReadRequest.class));
	}

	@Test
	void refusesAReadRequestThatNamesNothing() throws Exception {
		// A PATCH naming no keys is a write that would change nothing, answered
		// as though it had changed something.
		mvc.perform(patch("/api/v1/notifications/read")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"keys":[],"read":true}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("keys"));
	}

	@Test
	@DisplayName("BR-12: the settings state which leads and channels are on")
	void theSettingsStateWhichLeadsAreOn() throws Exception {
		given(notifications.settings()).willReturn(new NotificationSettingsResponse(
				List.of(10, 5, 2), new NotificationSettingsResponse.Channels(true, false, true)));

		mvc.perform(get("/api/v1/notifications/settings").cookie(session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leadDays").isArray())
				.andExpect(jsonPath("$.leadDays[0]").value(10))
				.andExpect(jsonPath("$.channels.push").value(true))
				.andExpect(jsonPath("$.channels.email").value(false))
				.andExpect(jsonPath("$.channels.weeklySummary").value(true));
	}

	@Test
	@DisplayName("BR-12: a lead time outside {10, 5, 2} is a 400 that names the field")
	void anImpossibleLeadTimeNamesTheField() throws Exception {
		willThrow(AppException.invalid("leadDays",
				"Lead times are any of 10, 5 and 2 days, and nothing else"))
				.given(notifications).updateSettings(any(UpdateNotificationSettingsRequest.class));

		mvc.perform(patch("/api/v1/notifications/settings")
				.cookie(session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"leadDays":[7]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("leadDays"));
	}

	@Test
	void refusesToAnswerWithoutASession() throws Exception {
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/notifications/settings")).andExpect(status().isUnauthorized());
	}
}
