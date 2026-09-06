package ie.budgetTracker.domain.identity;

/**
 * An email and a password hash. Never a password.
 *
 * A separate record from {@link User} so that reading a profile does not carry
 * a credential with it. Nothing that renders a user needs this type, and
 * nothing that needs this type renders it.
 */
public record Credentials(String email, String passwordHash) {

	/**
	 * Emails are compared lower-cased, so "Carlos@..." and "carlos@..." are one
	 * person. A unique index alone would not catch that.
	 */
	public static String normaliseEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
