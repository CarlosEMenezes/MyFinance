package ie.budgetTracker.application.cards;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.cards.dto.CardResponse;
import ie.budgetTracker.application.cards.dto.CreateCardRequest;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.domain.accounts.Account;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CardCycleDates;
import ie.budgetTracker.domain.cards.CardKind;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.DebitCard;
import ie.budgetTracker.domain.cards.StatementCycle;
import ie.budgetTracker.domain.cards.StatementCycleCalculator;
import ie.budgetTracker.domain.money.MoneyCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cards, and when their spending is actually billed (BR-4, BR-5).
 *
 * The three cycle dates are computed here, through the domain calculator the
 * shared vectors already pin down. ADR-7 puts every persisted figure on this
 * side: the Cards page renders these dates, it does not derive them.
 *
 * The clock is injected rather than read from a static call, because two of
 * the three dates are answers to "from today", and a test has to be able to
 * say when today is.
 */
@Service
public class CardService {

	private final CardRepository cards;
	private final AccountRepository accounts;
	private final CurrentUser currentUser;
	private final Clock clock;

	public CardService(CardRepository cards, AccountRepository accounts, CurrentUser currentUser,
			Clock clock) {
		this.cards = cards;
		this.accounts = accounts;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CardResponse> list() {
		Map<UUID, String> accountNames = accountNames();

		return cards.findAllForUser(currentUser.id()).stream()
				.map(card -> describe(card, accountNames))
				.toList();
	}

	/**
	 * BR-13: which cards settle from each account, for the Accounts page.
	 *
	 * The Accounts page names them so somebody can see what a balance is
	 * exposed to without opening a second screen. It is a list of names and
	 * nothing more - no figure here enters any total.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, List<String>> cardNamesByAccount() {
		Map<UUID, List<String>> byAccount = new LinkedHashMap<>();
		for (Card card : cards.findAllForUser(currentUser.id())) {
			byAccount.computeIfAbsent(card.accountId(), account -> new ArrayList<>())
					.add(card.name());
		}
		return byAccount;
	}

	@Transactional
	public CardResponse create(CreateCardRequest request) {
		Card card = requestedCard(request);

		Map<UUID, String> accountNames = accountNames();
		if (!accountNames.containsKey(card.accountId())) {
			// Not found rather than forbidden: a 403 would confirm the id exists
			// (ADR-11). The repository checks this again against the database,
			// which is the check that actually holds.
			throw AppException.notFound("No account with id " + card.accountId());
		}

		refuseADuplicateName(card.name());

		return describe(cards.create(currentUser.id(), card), accountNames);
	}

	/**
	 * The union the frontend sends, made into the domain type it means.
	 *
	 * Every combination JSON allows but the union does not is refused here, with
	 * the field named, because a body that means something impossible is worth
	 * rejecting loudly rather than half-honouring.
	 */
	private static Card requestedCard(CreateCardRequest request) {
		String name = requireName(request.name());

		if (request.kind() == CardKind.DEBIT) {
			refuseACycleOnADebitCard(request);
			return new DebitCard(null, name, request.accountId());
		}

		require(request.creditLimit(), "creditLimit",
				"A credit card needs the limit it is allowed to reach");
		require(request.closingDay(), "closingDay",
				"A credit card needs the day its statement closes (BR-4)");
		require(request.dueDay(), "dueDay",
				"A credit card needs the day its bill falls due (BR-4)");

		return new CreditCard(null, name, request.accountId(),
				Money.fromMinorUnits(request.creditLimit()),
				// BR-1: what is owed comes from transactions, so a new card owes
				// nothing. There is no field on the request to say otherwise.
				MoneyCalculator.ZERO,
				new StatementCycle(request.closingDay(), request.dueDay()));
	}

	/**
	 * BR-5: a debit card has no cycle, so a cycle field on one is not a harmless
	 * extra. Accepting it quietly would leave the sender believing in a cycle
	 * that does not exist and cannot be honoured.
	 */
	private static void refuseACycleOnADebitCard(CreateCardRequest request) {
		refuseOnADebitCard(request.creditLimit(), "creditLimit");
		refuseOnADebitCard(request.closingDay(), "closingDay");
		refuseOnADebitCard(request.dueDay(), "dueDay");
	}

	private static void refuseOnADebitCard(Object value, String field) {
		if (value != null) {
			throw AppException.invalid(field,
					"A debit card has no statement cycle - its spending leaves the account the "
							+ "same day (BR-5)");
		}
	}

	private static void require(Object value, String field, String why) {
		if (value == null) {
			throw AppException.invalid(field, why);
		}
	}

	private static String requireName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw AppException.invalid("name", "Give the card a name");
		}
		return trimmed;
	}

	private void refuseADuplicateName(String name) {
		boolean taken = cards.findAllForUser(currentUser.id()).stream()
				.anyMatch(existing -> existing.name().equalsIgnoreCase(name));
		if (taken) {
			throw AppException.conflict("A card called \"" + name + "\" already exists");
		}
	}

	private Map<UUID, String> accountNames() {
		return accounts.findAllForUser(currentUser.id()).stream()
				.collect(Collectors.toMap(Account::id, Account::name, (first, second) -> first,
						LinkedHashMap::new));
	}

	/** BR-4: a credit card is described with its dates; a debit card has none. */
	private CardResponse describe(Card card, Map<UUID, String> accountNames) {
		CardCycleDates dates = card instanceof CreditCard credit
				? StatementCycleCalculator.cycleDatesFor(LocalDate.now(clock), credit.cycle())
				: null;

		return CardResponse.from(card, accountNames.get(card.accountId()), dates);
	}
}
