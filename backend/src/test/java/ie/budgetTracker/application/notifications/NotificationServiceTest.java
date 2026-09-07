package ie.budgetTracker.application.notifications;

import static ie.budgetTracker.domain.money.MoneyCalculator.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.financing.FinancingRepository;
import ie.budgetTracker.application.notifications.dto.MarkNotificationsReadRequest;
import ie.budgetTracker.application.notifications.dto.NotificationResponse;
import ie.budgetTracker.application.notifications.dto.NotificationSettingsResponse;
import ie.budgetTracker.application.notifications.dto.UpdateNotificationSettingsRequest;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.financing.Loan;
import ie.budgetTracker.domain.financing.LoanTerms;
import ie.budgetTracker.domain.notifications.DueSource;
import ie.budgetTracker.domain.plan.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The derived queue (BR-12), with the ports mocked.
 *
 * "Today" is the 31st of August 2026 throughout, so a card due on the 5th is
 * five days out and a loan due on the 1st is one - which is what makes the
 * lead-time rules testable as numbers rather than as ranges.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-31T09:00:00Z");

	private static final UUID ADA = UUID.randomUUID();
	private static final UUID REVOLUT = UUID.randomUUID();
	private static final UUID VISA = UUID.fromString("11111111-2222-4333-8444-555555555555");

	@Mock
	private CardRepository cards;

	@Mock
	private FinancingRepository financing;

	@Mock
	private NotificationRepository notifications;

	private NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(new DuePayments(cards, financing), notifications,
				() -> ADA, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void allLeadsEnabled() {
		given(notifications.settings(ADA)).willReturn(new NotificationSettingsResponse(
				List.of(10, 5, 2), new NotificationSettingsResponse.Channels(true, false, true)));
	}

	private void nothingIsRead() {
		given(notifications.readAtByKey(ADA)).willReturn(Map.of());
	}

	/** Due on the 5th: five days out from the 31st of August. */
	private void aCardBillIsOwed() {
		given(cards.findAllForUser(ADA)).willReturn(List.of(new CreditCard(VISA, "Visa 4417",
				REVOLUT, of("2000.00"), of("386.40"), new StatementCycle(25, 5))));
	}

	/** Due on the 1st: one day out. */
	private void aLoanInstalmentIsOwed() {
		given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
				"Credit union", new LoanTerms(of("2500.00"), 24, of("118.40"), Frequency.MONTHLY,
						5),
				LocalDate.parse("2026-09-01"), REVOLUT)));
	}

	@Nested
	@DisplayName("the queue itself")
	class Queue {

		@Test
		@DisplayName("BR-12: derived from what is owed, soonest first")
		void isDerivedFromWhatIsOwedSoonestFirst() {
			allLeadsEnabled();
			nothingIsRead();
			aCardBillIsOwed();
			aLoanInstalmentIsOwed();

			List<NotificationResponse> queue = service.queue();

			assertThat(queue).hasSize(2);
			assertThat(queue.get(0).label()).isEqualTo("Credit union");
			assertThat(queue.get(0).daysUntilDue()).isEqualTo(1);
			assertThat(queue.get(1).sourceType()).isEqualTo(DueSource.CARD_BILL);
			assertThat(queue.get(1).daysUntilDue()).isEqualTo(5);
			assertThat(queue.get(1).amount()).isEqualTo(38640L);
		}

		@Test
		@DisplayName("BR-12: a cleared card is not in the queue at all")
		void aClearedCardDropsOutOnItsOwn() {
			allLeadsEnabled();
			nothingIsRead();
			given(cards.findAllForUser(ADA)).willReturn(List.of(new CreditCard(VISA, "Visa 4417",
					REVOLUT, of("2000.00"), of("0.00"), new StatementCycle(25, 5))));

			// Nothing had to delete a row: there is no row. The queue is derived,
			// so a paid card simply stops appearing.
			assertThat(service.queue()).isEmpty();
		}

		@Test
		@DisplayName("BR-12: a narrower lead time hides what is further away")
		void aNarrowerLeadHidesWhatIsFurtherAway() {
			given(notifications.settings(ADA)).willReturn(new NotificationSettingsResponse(
					List.of(2), new NotificationSettingsResponse.Channels(true, false, true)));
			nothingIsRead();
			aCardBillIsOwed();
			aLoanInstalmentIsOwed();

			// The loan is one day out and stays; the card is five and does not.
			assertThat(service.queue()).singleElement()
					.satisfies(item -> assertThat(item.label()).isEqualTo("Credit union"));
		}

		@Test
		@DisplayName("BR-12: with every warning off, money already due is still shown")
		void turningEveryWarningOffStillShowsWhatIsDue() {
			given(notifications.settings(ADA)).willReturn(new NotificationSettingsResponse(
					List.of(), new NotificationSettingsResponse.Channels(false, false, false)));
			nothingIsRead();
			given(financing.findLoansForUser(ADA)).willReturn(List.of(new Loan(UUID.randomUUID(),
					"Overdue", new LoanTerms(of("500.00"), 6, of("100.00"), Frequency.MONTHLY, 1),
					LocalDate.parse("2026-08-20"), REVOLUT)));

			// Turning every warning off must not hide money that has already left.
			assertThat(service.queue()).singleElement()
					.satisfies(item -> assertThat(item.daysUntilDue()).isNegative());
		}

		@Test
		@DisplayName("BR-12: says when an item was read, not merely that it was")
		void saysWhenAnItemWasRead() {
			Instant seen = Instant.parse("2026-08-30T21:00:00Z");
			allLeadsEnabled();
			aCardBillIsOwed();
			given(notifications.readAtByKey(ADA)).willReturn(Map.of("card-" + VISA, seen));

			assertThat(service.queue()).singleElement()
					.satisfies(item -> assertThat(item.readAt()).isEqualTo(seen));
		}

		@Test
		@DisplayName("BR-12: the badge counts what has not been seen")
		void theBadgeCountsWhatHasNotBeenSeen() {
			allLeadsEnabled();
			aCardBillIsOwed();
			aLoanInstalmentIsOwed();
			given(notifications.readAtByKey(ADA))
					.willReturn(Map.of("card-" + VISA, Instant.parse("2026-08-30T21:00:00Z")));

			assertThat(service.unreadCount()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("read state and settings")
	class ReadStateAndSettings {

		@Test
		@DisplayName("BR-12: marking one and marking many are the same write")
		void markingOneAndManyAreTheSameWrite() {
			service.markRead(new MarkNotificationsReadRequest(List.of("card-1", "loan-2"), true));

			Mockito.verify(notifications).markRead(eq(ADA), eq(List.of("card-1", "loan-2")),
					eq(true), any());
		}

		@Test
		@DisplayName("BR-12: refuses a lead time the rule does not offer")
		void refusesALeadTimeTheRuleDoesNotOffer() {
			allLeadsEnabled();

			// Silently dropping it would leave the sender believing in a warning
			// that will never arrive.
			assertThatThrownBy(() -> service.updateSettings(
					new UpdateNotificationSettingsRequest(List.of(7), null)))
					.isInstanceOf(AppException.class)
					.extracting(refused -> ((AppException) refused).field())
					.isEqualTo("leadDays");
		}

		@Test
		@DisplayName("keeps what the request did not mention")
		void keepsWhatTheRequestDidNotMention() {
			allLeadsEnabled();
			given(notifications.saveSettings(eq(ADA), any()))
					.willAnswer(call -> call.getArgument(1));

			service.updateSettings(new UpdateNotificationSettingsRequest(List.of(5), null));

			ArgumentCaptor<NotificationSettingsResponse> saved =
					ArgumentCaptor.forClass(NotificationSettingsResponse.class);
			Mockito.verify(notifications).saveSettings(eq(ADA), saved.capture());
			assertThat(saved.getValue().leadDays()).containsExactly(5);
			// The channels were not named, so they are untouched.
			assertThat(saved.getValue().channels().weeklySummary()).isTrue();
		}

		@Test
		@DisplayName("warning about nothing is a real choice, not a missing one")
		void warningAboutNothingIsARealChoice() {
			allLeadsEnabled();
			given(notifications.saveSettings(eq(ADA), any()))
					.willAnswer(call -> call.getArgument(1));

			assertThat(service.updateSettings(
					new UpdateNotificationSettingsRequest(List.of(), null)).leadDays()).isEmpty();
		}
	}
}
