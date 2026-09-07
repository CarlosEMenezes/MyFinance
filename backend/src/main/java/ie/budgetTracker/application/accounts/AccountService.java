package ie.budgetTracker.application.accounts;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.dto.AccountResponse;
import ie.budgetTracker.application.accounts.dto.CreateAccountRequest;
import ie.budgetTracker.application.accounts.dto.CreatePocketRequest;
import ie.budgetTracker.application.cards.CardService;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.accounts.AccountKind;
import ie.budgetTracker.domain.money.Currency;
import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.position.AccountBalance;
import ie.budgetTracker.domain.position.PositionCalculator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where money sits (BR-13).
 *
 * The counted total goes through {@link PositionCalculator}'s own account
 * summing rather than a local loop, so the figure on the Accounts page and the
 * figure in the position cannot disagree about which accounts count.
 */
@Service
public class AccountService {

	private final AccountRepository accounts;
	private final CardService cards;
	private final CurrentUser currentUser;

	public AccountService(AccountRepository accounts, CardService cards, CurrentUser currentUser) {
		this.accounts = accounts;
		this.cards = cards;
		this.currentUser = currentUser;
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> list() {
		// Read once for the whole list rather than per account: a card list per
		// row is the same answer fetched N times.
		Map<UUID, List<String>> cardNames = cards.cardNamesByAccount();

		return accountsForUser().stream()
				.map(account -> AccountResponse.from(account,
						cardNames.getOrDefault(account.id(), List.of())))
				.toList();
	}

	private List<Account> accountsForUser() {
		return accounts.findAllForUser(currentUser.id());
	}

	@Transactional
	public AccountResponse create(CreateAccountRequest request) {
		// A brand new account has nothing settling from it yet, and an empty list
		// says exactly that.
		return AccountResponse.from(create(request.name(), request.kind(),
				Money.fromMinorUnits(request.balance()), request.currency(),
				request.includeInTotals(), request.note()), List.of());
	}

	Account create(String name, AccountKind kind, BigDecimal balance, Currency currency,
			boolean includeInTotals, String note) {

		String trimmed = requireName(name);
		if (accountsForUser().stream().anyMatch(existing -> existing.name().equalsIgnoreCase(trimmed))) {
			throw AppException.conflict("An account called \"" + trimmed + "\" already exists");
		}

		return accounts.create(currentUser.id(), new Account(null, trimmed, kind,
				MoneyCalculator.of(balance), currency, includeInTotals, note, List.of()));
	}

	/**
	 * BR-13: adds a pocket to an account and leaves the account's balance alone.
	 *
	 * The pocket names part of money that is already in the account. Adding its
	 * balance to the parent would count the same euro twice, which is the single
	 * mistake this rule exists to prevent - so this method deliberately has no
	 * path that touches the parent balance at all.
	 */
	@Transactional
	public AccountResponse addPocket(UUID accountId, CreatePocketRequest request) {
		Account parent =
				addPocket(accountId, request.name(), Money.fromMinorUnits(request.balance()));

		return AccountResponse.from(parent,
				cards.cardNamesByAccount().getOrDefault(accountId, List.of()));
	}

	Account addPocket(UUID accountId, String name, BigDecimal balance) {
		Account parent = accounts.findForUser(currentUser.id(), accountId)
				.orElseThrow(() -> AppException.notFound("No account with id " + accountId));

		String trimmed = requireName(name);
		if (parent.pockets().stream().anyMatch(pocket -> pocket.name().equalsIgnoreCase(trimmed))) {
			throw AppException.conflict(
					"\"" + parent.name() + "\" already has a pocket called \"" + trimmed + "\"");
		}

		return accounts.addPocket(accountId, trimmed, MoneyCalculator.of(balance));
	}

	/** BR-13: only accounts marked for totals count, and pockets never do. */
	@Transactional(readOnly = true)
	public BigDecimal countedTotal() {
		return PositionCalculator.calculate(new ie.budgetTracker.domain.position.PositionInputs(
				accountsForUser().stream()
						.map(account -> new AccountBalance(account.balance(), account.includeInTotals()))
						.toList(),
				MoneyCalculator.ZERO, List.of(), List.of(), MoneyCalculator.ZERO))
				.availableNow();
	}

	private static String requireName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw AppException.conflict("A name is required");
		}
		return trimmed;
	}
}
