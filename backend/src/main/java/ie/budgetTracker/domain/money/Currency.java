package ie.budgetTracker.domain.money;

/**
 * The currencies the app knows.
 *
 * A closed set, not an ISO lookup: BR-8 needs a live rate for every one of
 * them, and a currency the FX provider cannot quote is one the app would have
 * to guess at. Widening this means widening the provider first.
 */
public enum Currency {
	EUR,
	USD,
	GBP,
	BRL
}
