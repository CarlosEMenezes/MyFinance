package ie.budgetTracker.application.cards.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CardCycleDates;
import ie.budgetTracker.domain.cards.CardKind;
import ie.budgetTracker.domain.cards.CreditCard;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `GET /cards`, shaped as frontend/src/types/api.ts froze it.
 *
 * One record for both kinds, because the wire contract is one shape with
 * nullable halves: the page reads `cycle === null` to decide which card it is
 * drawing. The domain keeps the two apart in the type system instead, and this
 * is the single point where that distinction flattens into nulls - which is
 * why the flattening happens by pattern-matching the sealed type rather than by
 * reading fields that might not be there (BR-4, BR-5).
 *
 * Absent rather than zero: `NON_NULL` keeps `creditLimit`, `closingDay` and
 * `cycle` off a debit card entirely. A zero limit would be a limit.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardResponse(
		UUID id,
		String name,
		CardKind kind,
		UUID accountId,
		/** The account name, so a card list needs no second request. */
		String settlesFrom,
		Long creditLimit,
		Long currentBalance,
		Integer closingDay,
		Integer dueDay,
		/** BR-4, computed server-side. Null on a debit card (BR-5). */
		CycleDatesResponse cycle) {

	/**
	 * The three dates the card screen shows.
	 *
	 * `LocalDate`, so they cross as ISO `YYYY-MM-DD` rather than as a timestamp
	 * with a time nobody meant and a zone that would move the day.
	 */
	public record CycleDatesResponse(
			LocalDate nextBillDate,
			LocalDate billDateOnClosingDay,
			LocalDate billDateAfterClosingDay) {
	}

	/**
	 * @param cycleDates BR-4 computed for a credit card, and null for a debit
	 *                   card, which has no cycle to compute (BR-5). Supplied by
	 *                   the service rather than worked out here: a DTO maps, it
	 *                   does not decide.
	 */
	public static CardResponse from(Card card, String settlesFrom, CardCycleDates cycleDates) {
		if (card instanceof CreditCard credit) {
			return new CardResponse(credit.id(), credit.name(), CardKind.CREDIT, credit.accountId(),
					settlesFrom,
					Money.toMinorUnits(credit.creditLimit()),
					Money.toMinorUnits(credit.currentBalance()),
					credit.cycle().closingDay(),
					credit.cycle().dueDay(),
					cycleDates == null ? null : new CycleDatesResponse(cycleDates.nextBillDate(),
							cycleDates.billDateOnClosingDay(),
							cycleDates.billDateAfterClosingDay()));
		}

		// BR-5: nothing to say about a cycle, because there is not one.
		return new CardResponse(card.id(), card.name(), CardKind.DEBIT, card.accountId(),
				settlesFrom, null, null, null, null, null);
	}
}
