package ie.budgetTracker.application.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * `POST /auth/register`.
 *
 * The minimum that makes an account: something to sign in with, something to
 * prove it, and something to call the person. Spec §0.7 asks for no more, and
 * everything else on the profile is editable afterwards on Settings.
 */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 320) String email,
		/**
		 * Long rather than complex. Length is what defeats guessing; character
		 * classes mostly defeat the person choosing, and push them toward
		 * "Password1!" which is worse than four ordinary words.
		 */
		@NotBlank @Size(min = 12, max = 200) String password,
		@NotBlank @Size(max = 200) String name) {
}
