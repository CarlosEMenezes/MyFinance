package ie.budgetTracker.application.accounts.dto;

import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.money.Currency;
import java.util.List;
import java.util.UUID;

/**
 * `GET /accounts`, shaped as frontend/src/types/api.ts froze it.
 *
 * In the application layer, not the api one: the dependency rule of spec §4 is
 * `api -> application -> domain`, and a DTO that reads a domain enum would put
 * the api layer straight onto the domain. Here the mapping sits on the inside
 * of that boundary and the controller stays pure HTTP.
 *
 * `balance` is the whole account and `pockets` names parts of that same
 * balance. Nothing here sums the two, and the pocket list carries no total of
 * its own, so there is no figure on this payload that a consumer could add up
 * into a double count (BR-13).
 */
public record AccountResponse(
		UUID id,
		String name,
		AccountKind kind,
		long balance,
		Currency currency,
		boolean includeInTotals,
		String note,
		List<PocketResponse> pockets,
		/** Names of the cards that settle from this account (spec §4). */
		List<String> cardNames) {

	public record PocketResponse(UUID id, UUID accountId, String name, long balance) {
	}

	/**
	 * @param cardNames the cards that settle from this account (BR-4, BR-5).
	 *                  Names only: nothing here is a figure, so nothing here can
	 *                  enter a total.
	 */
	public static AccountResponse from(Account account, List<String> cardNames) {
		return new AccountResponse(
				account.id(),
				account.name(),
				account.kind(),
				Money.toMinorUnits(account.balance()),
				account.currency(),
				account.includeInTotals(),
				account.note(),
				account.pockets().stream()
						.map(pocket -> new PocketResponse(pocket.id(), account.id(), pocket.name(),
								Money.toMinorUnits(pocket.balance())))
						.toList(),
				cardNames);
	}
}
