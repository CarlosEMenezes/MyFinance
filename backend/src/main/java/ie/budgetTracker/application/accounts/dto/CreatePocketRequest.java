package ie.budgetTracker.application.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * `POST /accounts/{id}/pockets`.
 *
 * There is no account id in the body: it is in the path, because BR-13 makes a
 * pocket part of an account rather than a thing beside one. A body that
 * carried its own parent id could disagree with the path.
 */
public record CreatePocketRequest(
		@NotBlank @Size(max = 200) String name,
		/** Minor units. Negative would be money set aside that is not there. */
		@PositiveOrZero long balance) {
}
