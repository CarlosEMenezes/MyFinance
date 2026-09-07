package ie.budgetTracker.application.transactions;

import ie.budgetTracker.application.AppException;
import ie.budgetTracker.application.accounts.AccountRepository;
import ie.budgetTracker.application.cards.CardRepository;
import ie.budgetTracker.application.fx.FxService;
import ie.budgetTracker.application.identity.CurrentUser;
import ie.budgetTracker.application.plan.CategoryRepository;
import ie.budgetTracker.application.support.Money;
import ie.budgetTracker.application.transactions.dto.CreateTransactionRequest;
import ie.budgetTracker.application.transactions.dto.TransactionResponse;
import ie.budgetTracker.domain.cards.Card;
import ie.budgetTracker.domain.cards.CreditCard;
import ie.budgetTracker.domain.cards.StatementCycleCalculator;
import ie.budgetTracker.domain.money.ExchangeRates;
import ie.budgetTracker.domain.money.MoneyCalculator;
import ie.budgetTracker.domain.plan.Category;
import ie.budgetTracker.domain.plan.CategoryType;
import ie.budgetTracker.domain.transactions.PaymentMethod;
import ie.budgetTracker.domain.transactions.PaymentMethodKind;
import ie.budgetTracker.domain.transactions.Transaction;
import ie.budgetTracker.domain.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging money that actually moved (BR-8, BR-4).
 *
 * Two rules meet here and both are the server's:
 *
 *   - **BR-8.** The amount arrives in the currency it was typed in. The rate is
 *     looked up here, the converted amount is computed here, and if no rate can
 *     be had the save is refused. Nothing in this file can produce a rate of 1
 *     for two different currencies.
 *   - **BR-4.** A credit-card expense is not owed on the day it is spent. The
 *     planned-expense date is the computed bill date, worked out through the
 *     same calculator the Cards page's figures come from.
 */
@Service
public class TransactionService {

	private final TransactionRepository transactions;
	private final CategoryRepository categories;
	private final CardRepository cards;
	private final AccountRepository accounts;
	private final FxService fx;
	private final CurrentUser currentUser;

	public TransactionService(TransactionRepository transactions, CategoryRepository categories,
			CardRepository cards, AccountRepository accounts, FxService fx,
			CurrentUser currentUser) {
		this.transactions = transactions;
		this.categories = categories;
		this.cards = cards;
		this.accounts = accounts;
		this.fx = fx;
		this.currentUser = currentUser;
	}

	@Transactional
	public TransactionResponse log(CreateTransactionRequest request) {
		refuseFinancingUntilItIsBuilt(request);

		UUID user = currentUser.id();
		Category category = categories.findForUser(user, request.categoryId())
				.orElseThrow(() -> AppException.notFound(
						"No category with id " + request.categoryId()));
		requireTheCategoryMatchesTheEntry(request.type(), category);

		Card card = cards.findForUser(user, request.paymentMethodId()).orElse(null);
		PaymentMethod method = card != null ? asPaymentMethod(card) : asAccount(user, request);

		ExchangeRates rates = fx.currentRates();
		BigDecimal amount = Money.fromMinorUnits(request.amount());
		BigDecimal rate = rates.rateFrom(request.currency(), rates.base())
				.orElseThrow(() -> AppException.unavailable(
						"No exchange rate from " + request.currency() + " to " + rates.base()
								+ " is available, so this amount cannot be converted. "
								+ "The entry has not been saved."));

		return TransactionResponse.from(transactions.create(user, new Transaction(
				null,
				request.type(),
				category.id(),
				amount,
				request.currency(),
				MoneyCalculator.of(amount.multiply(rate)),
				rate,
				request.date(),
				method,
				request.note(),
				null,
				null,
				plannedExpenseDate(request.type(), card, request.date()))));
	}

	/**
	 * BR-4: when a card expense is actually owed.
	 *
	 * Only a credit-card expense defers. An earning paid onto a card, or any
	 * debit spending, moves on the day it happened (BR-5), and a date here would
	 * put money on a bill that will never carry it.
	 */
	private static LocalDate plannedExpenseDate(TransactionType type, Card card, LocalDate date) {
		if (type != TransactionType.EXPENSE || !(card instanceof CreditCard credit)) {
			return null;
		}
		return StatementCycleCalculator.billDateFor(date, credit.cycle());
	}

	private static PaymentMethod asPaymentMethod(Card card) {
		return new PaymentMethod(card.id(), card instanceof CreditCard
				? PaymentMethodKind.CREDIT_CARD
				: PaymentMethodKind.DEBIT_CARD);
	}

	private PaymentMethod asAccount(UUID user, CreateTransactionRequest request) {
		return accounts.findForUser(user, request.paymentMethodId())
				.map(account -> new PaymentMethod(account.id(), PaymentMethodKind.ACCOUNT))
				// Not found rather than forbidden, and the same answer whether the id
				// belongs to somebody else or to nobody (ADR-11).
				.orElseThrow(() -> AppException.notFound(
						"No account or card with id " + request.paymentMethodId()));
	}

	/**
	 * An earning has to land in an earning category, and money going out in an
	 * expense one.
	 *
	 * Otherwise BR-9 would draw a variance against a plan that means the
	 * opposite of the figure, and the colour would say the good news was bad.
	 * A SAVING is money leaving the spending plan, so it belongs to the expense
	 * side too.
	 */
	private static void requireTheCategoryMatchesTheEntry(TransactionType type,
			Category category) {
		CategoryType expected = type == TransactionType.EARNING
				? CategoryType.EARNING
				: CategoryType.EXPENSE;

		if (category.type() != expected) {
			throw AppException.invalid("categoryId", "\"" + category.name() + "\" is a"
					+ (category.type() == CategoryType.EARNING ? "n earning" : "n expense")
					+ " category, so it cannot hold a " + type.name().toLowerCase(
							java.util.Locale.ROOT));
		}
	}

	/**
	 * BR-6 arrives with spec §6 step 7, and this refuses it until it does.
	 *
	 * Accepting the terms and quietly dropping them would be worse than
	 * refusing: the entry would be saved as an ordinary purchase, the instalment
	 * plan would never exist, and nothing on any screen would say so.
	 */
	private static void refuseFinancingUntilItIsBuilt(CreateTransactionRequest request) {
		if (request.financing() != null) {
			throw AppException.invalid("financing",
					"Spreading a purchase over instalments is not available yet");
		}
	}
}
