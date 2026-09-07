package ie.budgetTracker.application;

/**
 * A request that cannot be honoured, with the reason a caller needs.
 *
 * The kind maps to a status code in the api layer and nowhere else: the
 * application layer states what went wrong, and only the edge that speaks HTTP
 * decides how to say it in HTTP.
 */
public class AppException extends RuntimeException {

	public enum Kind {
		/** 404: the thing asked for does not exist. */
		NOT_FOUND,
		/** 409: it exists, but the rule says no. */
		CONFLICT,
		/** 401: who you are could not be established. */
		UNAUTHORISED,
		/** 400: the body was not a request this rule can make sense of. */
		INVALID
	}

	private final transient Kind kind;

	/**
	 * The field the caller has to fix, where there is one.
	 *
	 * Spec §4 asks a 400 to carry a field-level list, because a form told only
	 * "invalid" has to guess which control to mark. Bean validation supplies
	 * that for one field at a time; a rule spanning two fields - a credit card
	 * needing both cycle days - cannot be expressed that way without inventing a
	 * property name, so it is stated here instead.
	 */
	private final transient String field;

	public AppException(Kind kind, String message) {
		this(kind, message, null);
	}

	public AppException(Kind kind, String message, String field) {
		super(message);
		this.kind = kind;
		this.field = field;
	}

	public Kind kind() {
		return kind;
	}

	/** Null when the failure does not belong to one field. */
	public String field() {
		return field;
	}

	public static AppException notFound(String what) {
		return new AppException(Kind.NOT_FOUND, what);
	}

	public static AppException conflict(String why) {
		return new AppException(Kind.CONFLICT, why);
	}

	public static AppException unauthorised(String why) {
		return new AppException(Kind.UNAUTHORISED, why);
	}

	/** A 400 that names the field the caller has to fix. */
	public static AppException invalid(String field, String why) {
		return new AppException(Kind.INVALID, why, field);
	}
}
