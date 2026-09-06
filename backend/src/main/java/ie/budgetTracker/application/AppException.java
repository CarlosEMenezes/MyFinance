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
		CONFLICT
	}

	private final transient Kind kind;

	public AppException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public static AppException notFound(String what) {
		return new AppException(Kind.NOT_FOUND, what);
	}

	public static AppException conflict(String why) {
		return new AppException(Kind.CONFLICT, why);
	}
}
