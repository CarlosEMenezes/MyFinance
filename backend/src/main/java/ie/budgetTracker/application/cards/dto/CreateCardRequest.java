package ie.budgetTracker.application.cards.dto;

import ie.budgetTracker.domain.cards.CardKind;
import ie.budgetTracker.domain.cards.StatementCycle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * `POST /cards`.
 *
 * The frontend sends a discriminated union: a DEBIT body has no cycle fields at
 * all, and a CREDIT body cannot omit them. JSON has no unions, so this is one
 * record whose credit half is nullable, and {@code CardService} refuses the
 * combinations the union makes unrepresentable - naming the field it refused,
 * so the form knows which control to mark.
 *
 * The day range is validated here rather than only in the domain, because
 * bean validation is what produces the field-level list spec §4 asks for. The
 * domain checks it again on construction, and that is not duplication: this
 * one produces a good 400, that one makes an impossible cycle unbuildable.
 */
public record CreateCardRequest(
		@NotNull CardKind kind,
		@NotBlank @Size(max = 200) String name,
		@NotNull UUID accountId,
		/** Minor units. Credit cards only. */
		@PositiveOrZero Long creditLimit,
		/** BR-4: 1-28, so the day exists in every month. Credit cards only. */
		@Min(1) @Max(StatementCycle.LAST_CYCLE_DAY) Integer closingDay,
		@Min(1) @Max(StatementCycle.LAST_CYCLE_DAY) Integer dueDay) {

	/** The CREDIT arm of the union the frontend sends. */
	public static CreateCardRequest credit(String name, UUID accountId, long creditLimit,
			int closingDay, int dueDay) {
		return new CreateCardRequest(CardKind.CREDIT, name, accountId, creditLimit, closingDay,
				dueDay);
	}

	/** The DEBIT arm: no limit and no cycle, because there is no such thing (BR-5). */
	public static CreateCardRequest debit(String name, UUID accountId) {
		return new CreateCardRequest(CardKind.DEBIT, name, accountId, null, null, null);
	}
}
